package com.rainbutler.domain.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rainbutler.domain.user.entity.Role;
import com.rainbutler.global.config.PolicyProperties;
import com.rainbutler.global.security.JwtTokenProvider;
import com.rainbutler.global.storage.StorageService;
import com.rainbutler.support.IntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 관리자 사진 검토 목록 · 승인 · 반려 API를 검증합니다.
 */
@AutoConfigureMockMvc
class AdminReviewApiTest extends IntegrationTestSupport {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 12, 0);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PolicyProperties policyProperties;

    @MockitoBean
    private StorageService storageService;

    @MockitoBean
    private Clock clock;

    private Long adminId;
    private Long userId;

    @BeforeEach
    void setUp() {
        given(clock.getZone()).willReturn(SEOUL);
        given(clock.instant()).willReturn(NOW.atZone(SEOUL).toInstant());
        given(storageService.toReadUrl(anyString()))
                .willAnswer(invocation -> "http://" + invocation.getArgument(0));

        jdbcTemplate.execute(
                "TRUNCATE TABLE point_history, inspection, management_log, report, adoption, "
                        + "cleaning, drain, users RESTART IDENTITY CASCADE");
        adminId = user("관리자", "ADMIN");
        userId = user("나", "USER");
    }

    @Test
    void 관리자만_조회할_수_있다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reviews").header("Authorization", bearer(userId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void 검토_대기_목록은_세_종류를_최신순으로_내려준다() throws Exception {
        Long drainId = drain(userId);
        Long adoptionId = adoption(userId, drainId, cleaning(userId, drainId, "COMPLETED", "NOT_REQUIRED"));
        inspection(userId, drainId, null, "insp-before", "insp-after", NOW.minusMinutes(30));
        managementLog(adoptionId, "log-before", NOW.minusMinutes(20));
        cleaning(userId, drainId, "COMPLETED", "PENDING", NOW.minusMinutes(10));

        mockMvc.perform(get("/api/v1/admin/reviews").header("Authorization", bearer(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviews", hasSize(3)))
                .andExpect(jsonPath("$.data.reviews[*].type")
                        .value(contains("CLEANING", "MANAGEMENT_LOG", "INSPECTION")))
                .andExpect(jsonPath("$.data.reviews[1].drainName").value("내 또랑이"))
                .andExpect(jsonPath("$.data.reviews[2].drainName").value("역곡역 앞"))
                .andExpect(jsonPath("$.data.reviews[2].beforePhotoUrl")
                        .value("http://insp-before"))
                .andExpect(jsonPath("$.data.reviews[2].reason").isNotEmpty());
    }

    @Test
    void 종류로_걸러_조회한다() throws Exception {
        Long drainId = drain(userId);
        Long adoptionId = adoption(userId, drainId, cleaning(userId, drainId, "COMPLETED", "NOT_REQUIRED"));
        inspection(userId, drainId, null, "insp-before", null, NOW);
        managementLog(adoptionId, "log-before", NOW);

        mockMvc.perform(get("/api/v1/admin/reviews")
                        .param("type", "management-log")
                        .header("Authorization", bearer(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviews", hasSize(1)))
                .andExpect(jsonPath("$.data.reviews[0].type").value("MANAGEMENT_LOG"));
    }

    @Test
    void 점검을_승인하면_관리_주기와_경험치가_반영된다() throws Exception {
        Long drainId = drain(userId);
        Long inspectionId = inspection(userId, drainId, null, "insp-before", "insp-after", NOW);

        decide("INSPECTION", inspectionId, """
                {"decision": "APPROVE"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("INSPECTION"))
                .andExpect(jsonPath("$.data.reviewStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.xpAwarded").value(policyProperties.firstCleaningXp()));

        assertThat(text("SELECT status FROM inspection WHERE id = ?", inspectionId))
                .isEqualTo("COMPLETED");
        assertThat(id("SELECT reviewed_by FROM inspection WHERE id = ?", inspectionId))
                .isEqualTo(adminId);
        assertThat(text("SELECT photo_url FROM drain WHERE id = ?", drainId))
                .isEqualTo("insp-after");
        assertThat(count("SELECT count(*) FROM point_history WHERE user_id = ? "
                + "AND source_type = 'INSPECTION'", userId)).isEqualTo(1);
        assertThat(number("SELECT point FROM users WHERE id = ?", userId))
                .isEqualTo(policyProperties.firstCleaningXp());
    }

    @Test
    void 점검을_반려하면_REJECTED가_되고_경험치가_없다() throws Exception {
        Long drainId = drain(userId);
        Long inspectionId = inspection(userId, drainId, null, "insp-before", "insp-after", NOW);

        decide("INSPECTION", inspectionId, """
                {"decision": "REJECT", "reason": "사진이 흐려요."}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewStatus").value("REJECTED"))
                .andExpect(jsonPath("$.data.xpAwarded").value(0));

        assertThat(text("SELECT status FROM inspection WHERE id = ?", inspectionId))
                .isEqualTo("REJECTED");
        assertThat(count("SELECT count(*) FROM point_history WHERE user_id = ?", userId))
                .isZero();
    }

    @Test
    void 관리_기록을_승인하면_인증되고_관리_주기가_갱신된다() throws Exception {
        Long drainId = drain(userId);
        Long adoptionId = adoption(userId, drainId, cleaning(userId, drainId, "COMPLETED", "NOT_REQUIRED"));
        Long logId = managementLog(adoptionId, "log-before", NOW);

        decide("MANAGEMENT_LOG", logId, """
                {"decision": "APPROVE"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewStatus").value("APPROVED"));

        assertThat(text("SELECT status FROM management_log WHERE id = ?", logId))
                .isEqualTo("VERIFIED");
        assertThat(text("SELECT review_status FROM management_log WHERE id = ?", logId))
                .isEqualTo("APPROVED");
        assertThat(text("SELECT photo_url FROM drain WHERE id = ?", drainId))
                .isEqualTo("log-before");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT next_management_at FROM adoption WHERE id = ?",
                Timestamp.class, adoptionId)).isNotNull();
    }

    @Test
    void 청소를_반려하면_검토_결과가_남는다() throws Exception {
        Long drainId = drain(userId);
        Long cleaningId = cleaning(userId, drainId, "COMPLETED", "PENDING", NOW);

        decide("CLEANING", cleaningId, """
                {"decision": "REJECT", "reason": "현장 사진이 아니에요."}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewStatus").value("REJECTED"));

        assertThat(text("SELECT review_status FROM cleaning WHERE id = ?", cleaningId))
                .isEqualTo("REJECTED");
    }

    @Test
    void 반려_사유가_없으면_400이다() throws Exception {
        Long drainId = drain(userId);
        Long inspectionId = inspection(userId, drainId, null, "insp-before", null, NOW);

        decide("INSPECTION", inspectionId, """
                {"decision": "REJECT"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void 이미_처리된_검토는_409다() throws Exception {
        Long drainId = drain(userId);
        Long inspectionId = inspection(userId, drainId, null, "insp-before", null, NOW);

        decide("INSPECTION", inspectionId, "{\"decision\": \"APPROVE\"}")
                .andExpect(status().isOk());
        decide("INSPECTION", inspectionId, "{\"decision\": \"APPROVE\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REVIEW_ALREADY_PROCESSED"));
    }

    @Test
    void 잘못된_종류면_400이다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reviews")
                        .param("type", "REPORT")
                        .header("Authorization", bearer(adminId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    private ResultActions decide(String type, Long id, String body) throws Exception {
        MockHttpServletRequestBuilder request = patch("/api/v1/admin/reviews/" + type + "/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", bearer(adminId));
        return mockMvc.perform(request);
    }

    private String bearer(Long userId) {
        return "Bearer " + jwtTokenProvider.createAccessToken(userId, roleOf(userId));
    }

    private Role roleOf(Long userId) {
        String role = jdbcTemplate.queryForObject(
                "SELECT role FROM users WHERE id = ?", String.class, userId);
        return Role.valueOf(role);
    }

    private Long user(String nickname, String role) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO users (email, password, nickname, provider, role, point, level, "
                        + "onboarding_completed_at, identity_verified_at, notification_enabled, "
                        + "night_push_consent, created_at, updated_at) "
                        + "VALUES (?, '{noop}x', ?, 'LOCAL', ?, 0, 1, now(), now(), true, true, "
                        + "now(), now()) RETURNING id",
                Long.class, UUID.randomUUID() + "@test.com", nickname, role);
    }

    private Long drain(Long ownerId) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO drain (registered_by, latitude, longitude, photo_url, dirt_level, "
                        + "adoption_status, status, is_risk, name, created_at, updated_at) "
                        + "VALUES (?, 37.5, 127.0, 'http://x', 1, 'ADOPTED', 'ACTIVE', false, "
                        + "'역곡역 앞', now(), now()) RETURNING id",
                Long.class, ownerId);
    }

    private Long cleaning(Long ownerId, Long drainId, String status, String reviewStatus) {
        return cleaning(ownerId, drainId, status, reviewStatus, NOW.minusDays(1));
    }

    private Long cleaning(
            Long ownerId, Long drainId, String status, String reviewStatus, LocalDateTime createdAt) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO cleaning (user_id, drain_id, type, before_photo_url, dirt_level, "
                        + "dirt_confidence, after_photo_url, after_grade, attempt_no, status, "
                        + "review_status, latitude, longitude, location_mismatch, started_at, "
                        + "completed_at, created_at, updated_at) "
                        + "VALUES (?, ?, 'ADOPTION', 'clean-before', 2, 0.90, 'clean-after', 1, 1, "
                        + "?, ?, 37.5, 127.0, false, ?, ?, ?, ?) RETURNING id",
                Long.class, ownerId, drainId, status, reviewStatus,
                Timestamp.valueOf(createdAt), Timestamp.valueOf(createdAt),
                Timestamp.valueOf(createdAt), Timestamp.valueOf(createdAt));
    }

    private Long adoption(Long ownerId, Long drainId, Long cleaningId) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO adoption (user_id, drain_id, cleaning_id, nickname, status, level, "
                        + "score, ignore_count, adopted_at, created_at, updated_at) "
                        + "VALUES (?, ?, ?, '내 또랑이', 'ACTIVE', 1, 0, 0, now(), now(), now()) "
                        + "RETURNING id",
                Long.class, ownerId, drainId, cleaningId);
    }

    private Long inspection(
            Long ownerId, Long drainId, Long adoptionId,
            String beforeKey, String afterKey, LocalDateTime createdAt) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO inspection (user_id, drain_id, adoption_id, request_id, kind, status, "
                        + "before_photo_key, after_photo_key, before_grade, after_grade, "
                        + "ai_confidence, after_confidence, distance_m, captured_at, note, "
                        + "xp_awarded, rewarded, created_at, updated_at) "
                        + "VALUES (?, ?, ?, NULL, 'ROUTINE', 'PENDING', ?, ?, 2, NULL, 0.40, NULL, "
                        + "80, ?, NULL, 0, false, ?, ?) RETURNING id",
                Long.class, ownerId, drainId, adoptionId, beforeKey, afterKey,
                Timestamp.valueOf(createdAt), Timestamp.valueOf(createdAt),
                Timestamp.valueOf(createdAt));
    }

    private Long managementLog(Long adoptionId, String beforeKey, LocalDateTime createdAt) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO management_log (adoption_id, request_id, type, action_type, status, "
                        + "review_status, reviewed_by, reviewed_at, before_photo_url, "
                        + "after_photo_url, dirt_level, after_dirt_level, ai_confidence, memo, "
                        + "latitude, longitude, location_mismatch, managed_at, created_at, "
                        + "updated_at) "
                        + "VALUES (?, NULL, 'REGULAR', 'INSPECTION', 'SUBMITTED', 'PENDING', NULL, "
                        + "NULL, ?, NULL, 2, NULL, 0.40, NULL, 37.5, 127.0, false, NULL, ?, ?) "
                        + "RETURNING id",
                Long.class, adoptionId, beforeKey,
                Timestamp.valueOf(createdAt), Timestamp.valueOf(createdAt));
    }

    private String text(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, String.class, args);
    }

    private Long id(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }

    private int number(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }

    private int count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }
}
