package com.rainbutler.domain.inspection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rainbutler.domain.drain.service.DrainAiClassifier;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.global.security.JwtTokenProvider;
import com.rainbutler.global.storage.StorageService;
import com.rainbutler.global.storage.UploadPurpose;
import com.rainbutler.support.IntegrationTestSupport;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 점검 · 청소 기록 공통 제출 API의 상태 결정 · 후처리 · 중복 제출 차단을 검증합니다.
 *
 * <p>S3 · Gemini는 mock이라 외부 연결 없이 동작합니다.
 */
@AutoConfigureMockMvc
class InspectionApiTest extends IntegrationTestSupport {

    private static final double LAT = 37.5;
    private static final double LNG = 127.0;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private StorageService storageService;

    @MockitoBean
    private DrainAiClassifier drainAiClassifier;

    private Long userId;
    private String token;
    private Long forecastId;
    private String beforePhotoKey;
    private String afterPhotoKey;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.local(
                UUID.randomUUID() + "@test.com", "{noop}x", "점검집사"));
        userId = user.getId();
        token = jwtTokenProvider.createAccessToken(userId, user.getRole());

        // 점검 기록은 같은 사진 재사용을 막으므로 테스트마다 고유한 사진 key를 쓴다.
        String photoId = UUID.randomUUID().toString();
        beforePhotoKey = "test/before/" + photoId + "/before.jpg";
        afterPhotoKey = "test/after/" + photoId + "/after.jpg";

        forecastId = jdbcTemplate.queryForObject("INSERT INTO rain_forecast (rain_date, "
                + "rain_start_at, pop, pcp_mm, status, is_simulated, created_at, updated_at) "
                + "VALUES (current_date + 1, now() + interval '1 day', 80, 5.0, 'ACTIVE', false, "
                + "now(), now()) ON CONFLICT (rain_date, is_simulated) "
                + "WHERE status = 'ACTIVE' AND deleted_at IS NULL "
                + "DO UPDATE SET updated_at = now() RETURNING id", Long.class);

        given(storageService.verifyUploaded(anyLong(), anyString(), any(UploadPurpose.class)))
                .willAnswer(invocation -> invocation.getArgument(1));
        given(storageService.toReadUrl(anyString()))
                .willAnswer(invocation -> "http://" + invocation.getArgument(0));
        given(drainAiClassifier.assess(anyString()))
                .willReturn(new DrainAiClassifier.Assessment(2, false, 0.9, "판정", 1, false));
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        Long drainId = drain("ADOPTED");
        adoption(drainId);

        mockMvc.perform(post("/api/v1/inspections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(startBody(drainId, "ROUTINE", null, LAT, LNG, true)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void 깨끗한_점검_전_사진이면_완료하고_경험치와_관리_주기를_갱신한다() throws Exception {
        Long drainId = drain("ADOPTED");
        Long adoptionId = adoption(drainId);
        given(drainAiClassifier.assess("http://" + beforePhotoKey))
                .willReturn(new DrainAiClassifier.Assessment(1, false, 0.9, "깨끗함", 0, false));

        start(drainId, "ROUTINE", null, LAT, LNG, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.beforeGrade").value(0))
                .andExpect(jsonPath("$.data.xpAwarded").value(5));

        Map<String, Object> drain = jdbcTemplate.queryForMap(
                "SELECT photo_url, dirt_level FROM drain WHERE id = ?", drainId);
        assertThat(drain.get("photo_url")).isEqualTo(beforePhotoKey);
        assertThat(drain.get("dirt_level")).isEqualTo(1);

        Map<String, Object> adoption = jdbcTemplate.queryForMap(
                "SELECT last_managed_at, next_management_at, score FROM adoption WHERE id = ?",
                adoptionId);
        assertThat(toLocalDateTime(adoption.get("last_managed_at"))).isNotNull();
        assertThat(toLocalDateTime(adoption.get("next_management_at"))).isNotNull();
        assertThat(adoption.get("score")).isEqualTo(20);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT point FROM users WHERE id = ?", Integer.class, userId)).isEqualTo(20);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT amount FROM point_history WHERE user_id = ? AND source_type = 'INSPECTION'",
                Integer.class, userId)).isEqualTo(20);
    }

    @Test
    void 더러운_점검_전_사진이면_청소_후_사진을_받아_완료한다() throws Exception {
        Long drainId = drain("ADOPTED");
        adoption(drainId);
        given(drainAiClassifier.assess("http://" + beforePhotoKey))
                .willReturn(new DrainAiClassifier.Assessment(3, false, 0.9, "더러움", 2, false));
        given(drainAiClassifier.assess("http://" + afterPhotoKey))
                .willReturn(new DrainAiClassifier.Assessment(2, false, 0.9, "조금 남음", 1, false));

        String body = start(drainId, "ROUTINE", null, LAT, LNG, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NEEDS_CLEANING"))
                .andExpect(jsonPath("$.data.xpAwarded").value(0))
                .andReturn().getResponse().getContentAsString();
        Long inspectionId = inspectionId(body);

        mockMvc.perform(post("/api/v1/inspections/{inspectionId}/complete", inspectionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"afterKey\":\"" + afterPhotoKey
                                + "\",\"safetyConfirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.afterGrade").value(1))
                .andExpect(jsonPath("$.data.xpAwarded").value(5));

        Map<String, Object> drain = jdbcTemplate.queryForMap(
                "SELECT photo_url, dirt_level FROM drain WHERE id = ?", drainId);
        assertThat(drain.get("photo_url")).isEqualTo(afterPhotoKey);
        assertThat(drain.get("dirt_level")).isEqualTo(2);
    }

    @Test
    void 위치가_멀면_기록을_남기지_않고_400() throws Exception {
        Long drainId = drain("ADOPTED");
        Long adoptionId = adoption(drainId);
        given(drainAiClassifier.assess("http://" + beforePhotoKey))
                .willReturn(new DrainAiClassifier.Assessment(1, false, 0.9, "깨끗함", 0, false));

        start(drainId, "ROUTINE", null, LAT + 0.01, LNG + 0.01, true)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INSPECTION_LOCATION_MISMATCH"));

        Map<String, Object> adoption = jdbcTemplate.queryForMap(
                "SELECT last_managed_at FROM adoption WHERE id = ?", adoptionId);
        assertThat(adoption.get("last_managed_at")).isNull();
    }

    @Test
    void 비_예보_점검을_마치면_요청을_완료하고_무응답_횟수를_초기화한다() throws Exception {
        Long drainId = drain("ADOPTED");
        Long adoptionId = adoption(drainId);
        jdbcTemplate.update("UPDATE adoption SET ignore_count = 2 WHERE id = ?", adoptionId);
        Long requestId = request(adoptionId, drainId, "ACCEPTED");
        given(drainAiClassifier.assess("http://" + beforePhotoKey))
                .willReturn(new DrainAiClassifier.Assessment(1, false, 0.9, "깨끗함", 0, false));

        start(drainId, "FORECAST", requestId, LAT, LNG, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        Map<String, Object> request = jdbcTemplate.queryForMap(
                "SELECT status, completed_at FROM inspection_request WHERE id = ?", requestId);
        assertThat(request.get("status")).isEqualTo("COMPLETED");
        assertThat(request.get("completed_at")).isNotNull();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT ignore_count FROM adoption WHERE id = ?", Integer.class, adoptionId))
                .isZero();
    }

    @Test
    void 같은_요청으로_두_번_제출하면_409() throws Exception {
        Long drainId = drain("ADOPTED");
        Long adoptionId = adoption(drainId);
        Long requestId = request(adoptionId, drainId, "ACCEPTED");
        given(drainAiClassifier.assess("http://" + beforePhotoKey))
                .willReturn(new DrainAiClassifier.Assessment(2, false, 0.9, "더러움", 1, false));

        start(drainId, "FORECAST", requestId, LAT, LNG, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NEEDS_CLEANING"));

        start(drainId, "FORECAST", requestId, LAT, LNG, true)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    void 다른_사람의_입양이면_403() throws Exception {
        User other = userRepository.save(User.local(
                UUID.randomUUID() + "@test.com", "{noop}x", "다른집사"));
        Long drainId = drain("ADOPTED");
        adoption(other.getId(), drainId);

        start(drainId, "ROUTINE", null, LAT, LNG, true)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void 안전_수칙을_확인하지_않으면_400() throws Exception {
        Long drainId = drain("ADOPTED");
        adoption(drainId);

        start(drainId, "ROUTINE", null, LAT, LNG, false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    private ResultActions start(Long drainId, String kind, Long requestId, double latitude,
            double longitude, boolean safetyConfirmed) throws Exception {
        return mockMvc.perform(post("/api/v1/inspections")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(startBody(drainId, kind, requestId, latitude, longitude,
                        safetyConfirmed)));
    }

    private String startBody(Long drainId, String kind, Long requestId, double latitude,
            double longitude, boolean safetyConfirmed) {
        return "{\"drainId\":" + drainId + ",\"kind\":\"" + kind + "\","
                + (requestId == null ? "" : "\"requestId\":" + requestId + ",")
                + "\"beforeKey\":\"" + beforePhotoKey + "\","
                + "\"latitude\":" + latitude + ",\"longitude\":" + longitude + ","
                + "\"capturedAt\":\"" + java.time.OffsetDateTime.now() + "\",\"note\":\"점검했어요\","
                + "\"safetyConfirmed\":" + safetyConfirmed + "}";
    }

    private Long inspectionId(String responseBody) {
        int index = responseBody.indexOf("\"inspectionId\":");
        int start = index + "\"inspectionId\":".length();
        int end = start;
        while (end < responseBody.length() && Character.isDigit(responseBody.charAt(end))) {
            end++;
        }
        return Long.parseLong(responseBody.substring(start, end));
    }

    private Long drain(String adoptionStatus) {
        return drain(adoptionStatus, userId);
    }

    private Long drain(String adoptionStatus, Long registeredBy) {
        return jdbcTemplate.queryForObject("INSERT INTO drain (registered_by, latitude, "
                + "longitude, photo_url, dirt_level, adoption_status, status, is_risk, "
                + "created_at, updated_at) VALUES (?, ?, ?, 'http://old', 3, ?, 'ACTIVE', "
                + "false, now(), now()) RETURNING id", Long.class,
                registeredBy, LAT, LNG, adoptionStatus);
    }

    private Long adoption(Long drainId) {
        return adoption(userId, drainId);
    }

    private Long adoption(Long ownerId, Long drainId) {
        Long cleaningId = cleaning(ownerId, drainId);
        return jdbcTemplate.queryForObject("INSERT INTO adoption (user_id, drain_id, "
                + "cleaning_id, nickname, status, level, score, ignore_count, adopted_at, "
                + "created_at, updated_at) VALUES (?, ?, ?, '또랑이', 'ACTIVE', 1, 0, 0, now(), "
                + "now(), now()) RETURNING id", Long.class, ownerId, drainId, cleaningId);
    }

    private Long cleaning(Long ownerId, Long drainId) {
        return jdbcTemplate.queryForObject("INSERT INTO cleaning (user_id, drain_id, "
                + "type, before_photo_url, status, review_status, latitude, longitude, "
                + "location_mismatch, started_at, created_at, updated_at) VALUES (?, ?, "
                + "'ADOPTION', 'http://x', 'COMPLETED', 'NOT_REQUIRED', ?, ?, false, "
                + "now(), now(), now()) RETURNING id", Long.class, ownerId, drainId, LAT, LNG);
    }

    private Long request(Long adoptionId, Long drainId, String status) {
        LocalDateTime now = LocalDateTime.now();
        return jdbcTemplate.queryForObject("INSERT INTO inspection_request (forecast_id, "
                + "drain_id, adoption_id, status, reply, sent_at, reply_deadline_at, "
                + "complete_deadline_at, replied_at, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, 'ACCEPT', ?, ?, ?, ?, ?, ?) RETURNING id",
                Long.class, forecastId, drainId, adoptionId, status, now,
                now.plusHours(6), now.plusHours(14), now, now, now);
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }
}
