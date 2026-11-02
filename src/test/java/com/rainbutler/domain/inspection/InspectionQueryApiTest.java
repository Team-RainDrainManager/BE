package com.rainbutler.domain.inspection;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rainbutler.domain.user.entity.Role;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.global.security.JwtTokenProvider;
import com.rainbutler.support.IntegrationTestSupport;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 점검 기록 상세 · 입양별 이력 조회 API를 검증합니다.
 *
 * <p>사진 조회 URL 변환 · 365일 보존 마스킹 · 권한 · 커서 페이지를 확인합니다.
 */
@AutoConfigureMockMvc
class InspectionQueryApiTest extends IntegrationTestSupport {

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

    private Long userId;
    private String token;
    private String beforePhotoKey;
    private String afterPhotoKey;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.local(
                UUID.randomUUID() + "@test.com", "{noop}x", "점검집사"));
        userId = user.getId();
        token = jwtTokenProvider.createAccessToken(userId, user.getRole());

        String photoId = UUID.randomUUID().toString();
        beforePhotoKey = "https://cdn.example.com/before/" + photoId + ".jpg";
        afterPhotoKey = "https://cdn.example.com/after/" + photoId + ".jpg";
    }

    @Test
    void 점검_기록_상세를_사진_URL과_함께_조회한다() throws Exception {
        Long drainId = drain("ADOPTED");
        Long adoptionId = adoption(userId, drainId);
        Long inspectionId = inspection(userId, drainId, adoptionId, "COMPLETED",
                beforePhotoKey, afterPhotoKey, 3, 2, 5, LocalDateTime.now(), 5);

        detail(inspectionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inspectionId").value(inspectionId))
                .andExpect(jsonPath("$.data.drain.drainId").value(drainId))
                .andExpect(jsonPath("$.data.drain.name").value("또랑이"))
                .andExpect(jsonPath("$.data.kind").value("ROUTINE"))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.review").value("NOT_REQUIRED"))
                .andExpect(jsonPath("$.data.reviewReason").doesNotExist())
                .andExpect(jsonPath("$.data.beforePhotoUrl").value(beforePhotoKey))
                .andExpect(jsonPath("$.data.afterPhotoUrl").value(afterPhotoKey))
                .andExpect(jsonPath("$.data.beforeGrade").value(3))
                .andExpect(jsonPath("$.data.afterGrade").value(2))
                .andExpect(jsonPath("$.data.cleaned").value(true))
                .andExpect(jsonPath("$.data.distanceM").value(5))
                .andExpect(jsonPath("$.data.xp").value(5))
                .andExpect(jsonPath("$.data.note").value("점검했어요"))
                .andExpect(jsonPath("$.data.photoDeleted").value(false));
    }

    @Test
    void 검토_대기_기록은_검토_사유를_함께_준다() throws Exception {
        Long drainId = drain("ADOPTED");
        Long adoptionId = adoption(userId, drainId);
        Long inspectionId = inspection(userId, drainId, adoptionId, "PENDING",
                beforePhotoKey, null, 1, null, 120, LocalDateTime.now(), 0);

        detail(inspectionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.review").value("PENDING"))
                .andExpect(jsonPath("$.data.reviewReason").value(
                        "촬영 위치가 빗물받이에서 120m 떨어져 있어요."))
                .andExpect(jsonPath("$.data.afterPhotoUrl").doesNotExist())
                .andExpect(jsonPath("$.data.afterGrade").doesNotExist())
                .andExpect(jsonPath("$.data.cleaned").value(false));
    }

    @Test
    void 반려된_기록은_REJECTED와_반려_사유를_준다() throws Exception {
        Long drainId = drain("ADOPTED");
        Long adoptionId = adoption(userId, drainId);
        Long inspectionId = inspection(userId, drainId, adoptionId, "REJECTED",
                beforePhotoKey, afterPhotoKey, 3, 2, 5, LocalDateTime.now(), 0);

        detail(inspectionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.review").value("REJECTED"))
                .andExpect(jsonPath("$.data.reviewReason").value("관리자 검토에서 반려됐어요."));
    }

    @Test
    void 촬영일로부터_365일이_지나면_사진_URL_없이_photoDeleted_true를_준다() throws Exception {
        Long drainId = drain("ADOPTED");
        Long adoptionId = adoption(userId, drainId);
        Long inspectionId = inspection(userId, drainId, adoptionId, "COMPLETED",
                beforePhotoKey, afterPhotoKey, 3, 2, 5,
                LocalDateTime.now().minusDays(366), 5);

        detail(inspectionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.photoDeleted").value(true))
                .andExpect(jsonPath("$.data.beforePhotoUrl").value(nullValue()))
                .andExpect(jsonPath("$.data.afterPhotoUrl").value(nullValue()))
                .andExpect(jsonPath("$.data.cleaned").value(true));
    }

    @Test
    void 다른_사람의_점검_기록이면_403() throws Exception {
        User other = userRepository.save(User.local(
                UUID.randomUUID() + "@test.com", "{noop}x", "다른집사"));
        Long drainId = drain("ADOPTED");
        Long adoptionId = adoption(other.getId(), drainId);
        Long inspectionId = inspection(other.getId(), drainId, adoptionId, "COMPLETED",
                beforePhotoKey, null, 1, null, 5, LocalDateTime.now(), 5);

        detail(inspectionId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void 입양자는_대신_점검_기록도_볼_수_있다() throws Exception {
        User substitute = userRepository.save(User.local(
                UUID.randomUUID() + "@test.com", "{noop}x", "대신집사"));
        Long drainId = drain("ADOPTED");
        Long adoptionId = adoption(userId, drainId);
        Long inspectionId = inspection(substitute.getId(), drainId, adoptionId, "COMPLETED",
                beforePhotoKey, null, 1, null, 5, LocalDateTime.now(), 5);

        detail(inspectionId).andExpect(status().isOk());
    }

    @Test
    void 관리자와_시군_관계자는_다른_사람의_점검_기록을_볼_수_있다() throws Exception {
        Long drainId = drain("ADOPTED");
        Long adoptionId = adoption(userId, drainId);
        Long inspectionId = inspection(userId, drainId, adoptionId, "COMPLETED",
                beforePhotoKey, null, 1, null, 5, LocalDateTime.now(), 5);

        String adminToken = privilegedToken(Role.ADMIN);
        mockMvc.perform(get("/api/v1/inspections/{inspectionId}", inspectionId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        String officialToken = privilegedToken(Role.CITY_OFFICIAL);
        mockMvc.perform(get("/api/v1/inspections/{inspectionId}", inspectionId)
                        .header("Authorization", "Bearer " + officialToken))
                .andExpect(status().isOk());
    }

    @Test
    void 없는_점검_기록이면_404_INSPECTION_NOT_FOUND() throws Exception {
        detail(999_999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("INSPECTION_NOT_FOUND"));
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        Long drainId = drain("ADOPTED");
        Long adoptionId = adoption(userId, drainId);
        Long inspectionId = inspection(userId, drainId, adoptionId, "COMPLETED",
                beforePhotoKey, null, 1, null, 5, LocalDateTime.now(), 5);

        mockMvc.perform(get("/api/v1/inspections/{inspectionId}", inspectionId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void 입양별_점검_기록을_최신순_커서로_이어_받는다() throws Exception {
        Long drainId = drain("ADOPTED");
        Long adoptionId = adoption(userId, drainId);
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ids.add(inspection(userId, drainId, adoptionId, "COMPLETED",
                    beforePhotoKey + "?v=" + i, null, 1, null, 5,
                    LocalDateTime.now().minusDays(i), 5));
        }

        list(adoptionId, "size", "2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inspections[*].inspectionId")
                        .value(contains(ids.get(2).intValue(), ids.get(1).intValue())))
                .andExpect(jsonPath("$.data.inspections[0].review").value("NOT_REQUIRED"))
                .andExpect(jsonPath("$.data.nextCursor").value(ids.get(1).intValue()));

        list(adoptionId, "size", "2", "cursor", String.valueOf(ids.get(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inspections[*].inspectionId")
                        .value(contains(ids.get(0).intValue())))
                .andExpect(jsonPath("$.data.nextCursor").value(nullValue()));
    }

    @Test
    void 이력_목록의_size가_1보다_작으면_400() throws Exception {
        Long drainId = drain("ADOPTED");
        Long adoptionId = adoption(userId, drainId);

        list(adoptionId, "size", "0")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void 다른_사람의_입양_이력이면_403() throws Exception {
        User other = userRepository.save(User.local(
                UUID.randomUUID() + "@test.com", "{noop}x", "다른집사"));
        Long drainId = drain("ADOPTED");
        Long adoptionId = adoption(other.getId(), drainId);

        list(adoptionId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void 없는_입양_이력이면_404_ADOPTION_NOT_FOUND() throws Exception {
        list(999_999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ADOPTION_NOT_FOUND"));
    }

    private ResultActions detail(Long inspectionId) throws Exception {
        return mockMvc.perform(get("/api/v1/inspections/{inspectionId}", inspectionId)
                .header("Authorization", "Bearer " + token));
    }

    private ResultActions list(Long adoptionId, String... params) throws Exception {
        var request = get("/api/v1/adoptions/{adoptionId}/inspections", adoptionId)
                .header("Authorization", "Bearer " + token);
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request);
    }

    private String privilegedToken(Role role) {
        User user = userRepository.save(User.local(
                UUID.randomUUID() + "@test.com", "{noop}x", role.name()));
        jdbcTemplate.update("UPDATE users SET role = ? WHERE id = ?", role.name(), user.getId());
        return jwtTokenProvider.createAccessToken(user.getId(), role);
    }

    private Long drain(String adoptionStatus) {
        return jdbcTemplate.queryForObject("INSERT INTO drain (registered_by, latitude, "
                + "longitude, photo_url, dirt_level, adoption_status, status, is_risk, "
                + "created_at, updated_at) VALUES (?, ?, ?, 'http://old', 3, ?, 'ACTIVE', "
                + "false, now(), now()) RETURNING id", Long.class,
                userId, LAT, LNG, adoptionStatus);
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

    private Long inspection(Long ownerId, Long drainId, Long adoptionId, String status,
            String beforeKey, String afterKey, Integer beforeGrade, Integer afterGrade,
            int distanceM, LocalDateTime capturedAt, int xp) {
        return jdbcTemplate.queryForObject("INSERT INTO inspection (user_id, drain_id, "
                + "adoption_id, request_id, kind, status, before_photo_key, after_photo_key, "
                + "before_grade, after_grade, ai_confidence, after_confidence, distance_m, "
                + "captured_at, note, xp_awarded, rewarded, created_at, updated_at) "
                + "VALUES (?, ?, ?, NULL, 'ROUTINE', ?, ?, ?, ?, ?, 0.9, ?, ?, ?, "
                + "'점검했어요', ?, true, now(), now()) RETURNING id", Long.class,
                ownerId, drainId, adoptionId, status, beforeKey, afterKey, beforeGrade,
                afterGrade, afterGrade == null ? null : 0.9, distanceM, capturedAt, xp);
    }
}
