package com.rainbutler.domain.adoption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rainbutler.domain.adoption.service.ManagementCyclePolicy;
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
 * 관리 기록 API의 저장 · 관리 주기 갱신 · 빗물받이 최신 사진 · 비 예보 요청 완료를 검증합니다.
 *
 * <p>S3 · Gemini는 mock이라 외부 연결 없이 동작합니다.
 */
@AutoConfigureMockMvc
class ManagementLogApiTest extends IntegrationTestSupport {

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

    @Autowired
    private ManagementCyclePolicy managementCyclePolicy;

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
                UUID.randomUUID() + "@test.com", "{noop}x", "관리집사"));
        userId = user.getId();
        token = jwtTokenProvider.createAccessToken(userId, user.getRole());

        // 관리 기록은 같은 사진 재사용을 막으므로 테스트마다 고유한 사진 key를 쓴다.
        String photoId = UUID.randomUUID().toString();
        beforePhotoKey = "test/before/" + photoId + "/before.jpg";
        afterPhotoKey = "test/after/" + photoId + "/after.jpg";

        forecastId = jdbcTemplate.queryForObject("INSERT INTO rain_forecast (rain_date, "
                + "rain_start_at, pop, pcp_mm, status, is_simulated, created_at, updated_at) "
                + "VALUES (current_date + 1, now() + interval '1 day', 80, 5.0, 'ACTIVE', false, "
                + "now(), now()) ON CONFLICT (rain_date, grid_nx, grid_ny, is_simulated) "
                + "WHERE status = 'ACTIVE' AND deleted_at IS NULL "
                + "DO UPDATE SET updated_at = now() RETURNING id", Long.class);

        given(storageService.verifyUploaded(anyLong(), anyString(), any(UploadPurpose.class)))
                .willAnswer(invocation -> invocation.getArgument(1));
        given(storageService.toReadUrl(anyString()))
                .willAnswer(invocation -> "http://" + invocation.getArgument(0));
        given(drainAiClassifier.assess(anyString()))
                .willReturn(new DrainAiClassifier.Assessment(2, false, 0.9, "판정", 2, false));
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        Long drainId = drain("ADOPTED");
        Long adoptionId = adoption(drainId, null, null);

        mockMvc.perform(post("/api/v1/adoptions/{adoptionId}/management-logs", adoptionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(regularBody(null)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void 정기_점검을_기록하면_관리_주기와_빗물받이_최신_사진을_갱신한다() throws Exception {
        Long drainId = drain("ADOPTED");
        Long adoptionId = adoption(drainId, null, null);

        record(adoptionId, regularBody(null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.type").value("REGULAR"))
                .andExpect(jsonPath("$.data.actionType").value("INSPECTION"))
                .andExpect(jsonPath("$.data.status").value("VERIFIED"))
                .andExpect(jsonPath("$.data.reviewStatus").value("NOT_REQUIRED"))
                .andExpect(jsonPath("$.data.locationMismatch").value(false))
                .andExpect(jsonPath("$.data.beforeDirtLevel").value(2))
                .andExpect(jsonPath("$.data.managedAt").exists())
                .andExpect(jsonPath("$.data.nextManagementAt").exists());

        Map<String, Object> log = jdbcTemplate.queryForMap(
                "SELECT before_photo_url, after_photo_url, status, review_status, managed_at "
                        + "FROM management_log WHERE adoption_id = ?", adoptionId);
        assertThat(log.get("before_photo_url")).isEqualTo(beforePhotoKey);
        assertThat(log.get("after_photo_url")).isNull();
        assertThat(log.get("status")).isEqualTo("VERIFIED");
        assertThat(log.get("review_status")).isEqualTo("NOT_REQUIRED");
        assertThat(log.get("managed_at")).isNotNull();

        Map<String, Object> adoption = jdbcTemplate.queryForMap(
                "SELECT adopted_at, last_managed_at, next_management_at FROM adoption WHERE id = ?",
                adoptionId);
        LocalDateTime adoptedAt = toLocalDateTime(adoption.get("adopted_at"));
        LocalDateTime lastManagedAt = toLocalDateTime(adoption.get("last_managed_at"));
        LocalDateTime nextManagementAt = toLocalDateTime(adoption.get("next_management_at"));
        assertThat(lastManagedAt).isNotNull();
        assertThat(nextManagementAt)
                .isEqualTo(managementCyclePolicy.nextManagementAt(adoptedAt, lastManagedAt));

        Map<String, Object> drain = jdbcTemplate.queryForMap(
                "SELECT photo_url, dirt_level FROM drain WHERE id = ?", drainId);
        assertThat(drain.get("photo_url")).isEqualTo(beforePhotoKey);
        assertThat(drain.get("dirt_level")).isEqualTo(2);
    }

    @Test
    void 청소까지_하면_청소_후_사진과_등급으로_빗물받이를_갱신한다() throws Exception {
        Long drainId = drain("ADOPTED");
        Long adoptionId = adoption(drainId, null, null);
        given(drainAiClassifier.assess("http://" + beforePhotoKey))
                .willReturn(new DrainAiClassifier.Assessment(2, false, 0.9, "판정", 2, false));
        given(drainAiClassifier.assess("http://" + afterPhotoKey))
                .willReturn(new DrainAiClassifier.Assessment(1, false, 0.9, "판정", 1, false));

        record(adoptionId, cleaningBody(null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VERIFIED"))
                .andExpect(jsonPath("$.data.beforeDirtLevel").value(2))
                .andExpect(jsonPath("$.data.afterDirtLevel").value(1));

        Map<String, Object> drain = jdbcTemplate.queryForMap(
                "SELECT photo_url, dirt_level FROM drain WHERE id = ?", drainId);
        assertThat(drain.get("photo_url")).isEqualTo(afterPhotoKey);
        assertThat(drain.get("dirt_level")).isEqualTo(1);
    }

    @Test
    void 청소_후_등급이_좋아지지_않으면_FAILED_이고_관리_주기를_갱신하지_않는다() throws Exception {
        Long drainId = drain("ADOPTED");
        LocalDateTime lastManagedAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime nextManagementAt = LocalDateTime.of(2026, 1, 15, 10, 0);
        Long adoptionId = adoption(drainId, lastManagedAt, nextManagementAt);

        record(adoptionId, cleaningBody(null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.managedAt").isEmpty());

        Map<String, Object> adoption = jdbcTemplate.queryForMap(
                "SELECT last_managed_at, next_management_at FROM adoption WHERE id = ?",
                adoptionId);
        assertThat(toLocalDateTime(adoption.get("last_managed_at"))).isEqualTo(lastManagedAt);
        assertThat(toLocalDateTime(adoption.get("next_management_at")))
                .isEqualTo(nextManagementAt);

        Map<String, Object> drain = jdbcTemplate.queryForMap(
                "SELECT photo_url, dirt_level FROM drain WHERE id = ?", drainId);
        assertThat(drain.get("photo_url")).isEqualTo("http://old");
        assertThat(drain.get("dirt_level")).isEqualTo(3);
    }

    @Test
    void 위치가_멀면_검토_대기로_남긴다() throws Exception {
        Long drainId = drain("ADOPTED");
        Long adoptionId = adoption(drainId, null, null);

        record(adoptionId, regularBody(null, LAT + 0.01, LNG + 0.01))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.locationMismatch").value(true))
                .andExpect(jsonPath("$.data.reviewStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.status").value("VERIFIED"));
    }

    @Test
    void 비_예보_점검은_연결된_요청을_완료하고_무응답_횟수를_초기화한다() throws Exception {
        Long drainId = drain("ADOPTED");
        Long adoptionId = adoption(drainId, null, null);
        jdbcTemplate.update("UPDATE adoption SET ignore_count = 2 WHERE id = ?", adoptionId);
        Long requestId = request(adoptionId, drainId, "ACCEPTED");

        record(adoptionId, rainBody(requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("RAIN"));

        Map<String, Object> request = jdbcTemplate.queryForMap(
                "SELECT status, completed_at FROM inspection_request WHERE id = ?", requestId);
        assertThat(request.get("status")).isEqualTo("COMPLETED");
        assertThat(request.get("completed_at")).isNotNull();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT request_id FROM management_log WHERE adoption_id = ?",
                Long.class, adoptionId)).isEqualTo(requestId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT ignore_count FROM adoption WHERE id = ?", Integer.class, adoptionId))
                .isZero();
    }

    @Test
    void 이미_기록한_비_예보_요청이면_409() throws Exception {
        Long drainId = drain("ADOPTED");
        Long adoptionId = adoption(drainId, null, null);
        Long requestId = request(adoptionId, drainId, "ACCEPTED");
        jdbcTemplate.update("INSERT INTO management_log (adoption_id, request_id, type, "
                + "action_type, status, review_status, location_mismatch, created_at, updated_at) "
                + "VALUES (?, ?, 'RAIN', 'INSPECTION', 'VERIFIED', 'NOT_REQUIRED', false, "
                + "now(), now())", adoptionId, requestId);

        record(adoptionId, rainBody(requestId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    void 비_예보인데_요청_id가_없으면_400() throws Exception {
        Long drainId = drain("ADOPTED");
        Long adoptionId = adoption(drainId, null, null);

        record(adoptionId, rainBody(null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void 다른_사람의_입양이면_403() throws Exception {
        User other = userRepository.save(User.local(
                UUID.randomUUID() + "@test.com", "{noop}x", "다른집사"));
        Long drainId = drain("ADOPTED", other.getId());
        Long adoptionId = adoption(other.getId(), drainId, null, null);

        record(adoptionId, regularBody(null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void 안전_동의가_없으면_400() throws Exception {
        Long drainId = drain("ADOPTED");
        Long adoptionId = adoption(drainId, null, null);

        record(adoptionId, regularBody(null, LAT, LNG, false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    private ResultActions record(Long adoptionId, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/adoptions/{adoptionId}/management-logs", adoptionId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String regularBody(Long requestId) {
        return regularBody(requestId, LAT, LNG, true);
    }

    private String regularBody(Long requestId, double latitude, double longitude) {
        return regularBody(requestId, latitude, longitude, true);
    }

    private String regularBody(Long requestId, double latitude, double longitude,
            boolean safetyAgreed) {
        return "{\"type\":\"REGULAR\",\"actionType\":\"INSPECTION\","
                + "\"beforePhotoKey\":\"" + beforePhotoKey + "\",\"memo\":\"깨끗해요\","
                + "\"latitude\":" + latitude + ",\"longitude\":" + longitude + ","
                + "\"safetyAgreed\":" + safetyAgreed + "}";
    }

    private String cleaningBody(Long requestId) {
        return "{\"type\":\"REGULAR\",\"actionType\":\"CLEANING\","
                + "\"beforePhotoKey\":\"" + beforePhotoKey + "\","
                + "\"afterPhotoKey\":\"" + afterPhotoKey + "\",\"memo\":\"청소했어요\","
                + "\"latitude\":" + LAT + ",\"longitude\":" + LNG + ",\"safetyAgreed\":true}";
    }

    private String rainBody(Long requestId) {
        return "{\"type\":\"RAIN\",\"requestId\":" + requestId + ","
                + "\"actionType\":\"INSPECTION\","
                + "\"beforePhotoKey\":\"" + beforePhotoKey + "\",\"memo\":\"비 예보 점검\","
                + "\"latitude\":" + LAT + ",\"longitude\":" + LNG + ",\"safetyAgreed\":true}";
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

    private Long adoption(Long drainId, LocalDateTime lastManagedAt,
            LocalDateTime nextManagementAt) {
        return adoption(userId, drainId, lastManagedAt, nextManagementAt);
    }

    private Long adoption(Long ownerId, Long drainId, LocalDateTime lastManagedAt,
            LocalDateTime nextManagementAt) {
        Long cleaningId = cleaning(ownerId, drainId);
        return jdbcTemplate.queryForObject("INSERT INTO adoption (user_id, drain_id, "
                + "cleaning_id, nickname, status, level, score, ignore_count, adopted_at, "
                + "last_managed_at, next_management_at, created_at, updated_at) "
                + "VALUES (?, ?, ?, '또랑이', 'ACTIVE', 1, 0, 0, now(), ?::timestamp, "
                + "?::timestamp, now(), now()) RETURNING id", Long.class, ownerId, drainId,
                cleaningId, lastManagedAt, nextManagementAt);
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
