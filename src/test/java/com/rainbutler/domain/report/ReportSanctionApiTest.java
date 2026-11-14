package com.rainbutler.domain.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.rainbutler.domain.drain.service.DrainAiClassifier;
import com.rainbutler.domain.inspection.service.InspectionEligibilityService;
import com.rainbutler.domain.request.service.SubstituteClaimService;
import com.rainbutler.domain.user.entity.Role;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.security.JwtTokenProvider;
import com.rainbutler.global.storage.StorageService;
import com.rainbutler.support.IntegrationTestSupport;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
 * 관리자 제보 처리의 허위 제보 제재(반려 + 제재 · 알림 · 응답)를 검증합니다.
 *
 * <p>제보는 SQL로 바로 만듭니다 (제보 접수 흐름은 {@code ReportApiTest}가 검증).
 */
@AutoConfigureMockMvc
class ReportSanctionApiTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private InspectionEligibilityService inspectionEligibilityService;

    @Autowired
    private SubstituteClaimService substituteClaimService;

    /** ReportApiTest와 같은 목 구성 (테스트 컨텍스트를 함께 쓰려고) */
    @MockitoBean
    private StorageService storageService;

    @MockitoBean
    private DrainAiClassifier drainAiClassifier;

    private Long reporterId;
    private Long drainId;
    private String adminToken;

    @BeforeEach
    void setUp() {
        reporterId = user(Role.USER);
        // 알림을 받고 온보딩을 마친 제보자 (대신 점검 제재 확인용)
        jdbcTemplate.update("UPDATE users SET notification_enabled = true, "
                + "night_push_consent = true, onboarding_completed_at = now(), "
                + "identity_verified_at = now() WHERE id = ?",
                reporterId);
        drainId = jdbcTemplate.queryForObject("INSERT INTO drain (latitude, longitude, photo_url, "
                + "dirt_level, adoption_status, status, is_risk, created_at, updated_at) VALUES "
                + "(37.5, 127.0, 'http://x', 1, 'AVAILABLE', 'ACTIVE', false, now(), now()) "
                + "RETURNING id", Long.class);
        adminToken = token(Role.ADMIN);
    }

    @Test
    void 승인과_반려는_sanction_없이_보내면_그대로이고_결과를_알린다() throws Exception {
        review(report(), adminToken, "{\"approved\":true,\"adminComment\":\"확인했습니다\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.sanction").value(nullValue()));
        Long rejected = report();
        review(rejected, adminToken, "{\"approved\":false,\"adminComment\":\"막힘이 보이지 않음\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.sanction").value(nullValue()));

        assertThat(sanctionCount()).isZero();
        assertThat(notificationCount("SANCTION_IMPOSED")).isZero();
        assertThat(jdbcTemplate.queryForList("SELECT title || ' | ' || body FROM notification "
                + "WHERE user_id = ? AND type = 'REPORT_RESULT' ORDER BY id", String.class,
                reporterId)).containsExactly(
                "제보가 반영됐어요 | 막힘 제보를 확인했어요. 고마워요! 경험치가 5 올랐어요.",
                "제보가 반려됐어요 | 막힘 제보를 반려했어요. 사유: 막힘이 보이지 않음");
    }

    @Test
    void 허위_제보는_반려하고_7일_14일_영구로_제재하고_알림을_하나만_보낸다() throws Exception {
        Long first = report();
        String json = sanction(first, "다른 장소 사진으로 허위 제보")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.adminComment").value("다른 장소 사진으로 허위 제보"))
                .andExpect(jsonPath("$.data.sanction.permanent").value(false))
                .andReturn().getResponse().getContentAsString();
        LocalDateTime firstStart = time(json, "$.data.sanction.startsAt");
        LocalDateTime firstUntil = time(json, "$.data.sanction.until");
        assertThat(firstUntil).isEqualTo(firstStart.plusDays(7));
        assertThat(notificationCount("SANCTION_IMPOSED")).isEqualTo(1);
        assertThat(notificationCount("REPORT_RESULT")).isZero();
        Map<String, Object> notice = jdbcTemplate.queryForMap("SELECT title, body "
                + "FROM notification WHERE user_id = ? AND type = 'SANCTION_IMPOSED'", reporterId);
        assertThat(notice.get("title")).isEqualTo("허위 제보로 이용이 제한됐어요");
        assertThat((String) notice.get("body"))
                .endsWith("까지 점검 · 대신 점검을 할 수 없어요. 사유: 다른 장소 사진으로 허위 제보");

        // 두 번째: 14일, 첫 제재가 끝나는 시각부터 이어서.
        // 이어지는 기준은 DB에 저장된 종료 시각이다 (DB는 마이크로초까지라 응답의 나노초와 다를 수 있음)
        LocalDateTime storedUntil = jdbcTemplate.queryForObject("SELECT until FROM sanction "
                + "WHERE report_id = ?", LocalDateTime.class, first);
        assertThat(storedUntil).isCloseTo(firstUntil, within(1, ChronoUnit.MILLIS));
        String second = sanction(report(), "또 허위 제보").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(time(second, "$.data.sanction.startsAt")).isEqualTo(storedUntil);
        assertThat(time(second, "$.data.sanction.until")).isEqualTo(storedUntil.plusDays(14));

        // 세 번째: 영구
        sanction(report(), "세 번째 허위 제보").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sanction.permanent").value(true))
                .andExpect(jsonPath("$.data.sanction.until").value(nullValue()));
        assertThat(jdbcTemplate.queryForObject("SELECT body FROM notification "
                + "WHERE user_id = ? AND type = 'SANCTION_IMPOSED' ORDER BY id DESC LIMIT 1",
                String.class, reporterId))
                .isEqualTo("점검 · 대신 점검을 더 이상 할 수 없어요. 사유: 세 번째 허위 제보");
        assertThat(sanctionCount()).isEqualTo(3);
    }

    @Test
    void 이미_처리한_제보로는_다시_제재할_수_없다() throws Exception {
        Long reportId = report();
        sanction(reportId, "허위 제보").andExpect(status().isOk());

        sanction(reportId, "허위 제보")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REPORT_ALREADY_PROCESSED"));
        assertThat(sanctionCount()).isEqualTo(1);
    }

    @Test
    void 승인과_제재를_함께_보내거나_사유가_없으면_400() throws Exception {
        Long reportId = report();
        review(reportId, adminToken, "{\"approved\":true,\"sanction\":true,\"adminComment\":\"x\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
        review(reportId, adminToken,
                "{\"approved\":false,\"sanction\":true,\"adminComment\":\" \"}")
                .andExpect(status().isBadRequest());

        assertThat(reportStatus(reportId)).isEqualTo("PENDING");
        assertThat(sanctionCount()).isZero();
    }

    @Test
    void 시청_관계자는_제재할_수_없지만_승인_반려는_한다() throws Exception {
        String cityToken = token(Role.CITY_OFFICIAL);
        Long reportId = report();

        sanction(reportId, cityToken, "허위 제보")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        assertThat(reportStatus(reportId)).isEqualTo("PENDING");

        review(reportId, cityToken, "{\"approved\":false,\"adminComment\":\"막힘 아님\"}")
                .andExpect(status().isOk());
        review(report(), cityToken, "{\"approved\":true}").andExpect(status().isOk());
        assertThat(sanctionCount()).isZero();
    }

    @Test
    void 탈퇴한_제보자는_반려만_하고_제재하지_않는다() throws Exception {
        Long reportId = report();
        jdbcTemplate.update("UPDATE users SET deleted_at = now() WHERE id = ?", reporterId);

        sanction(reportId, "허위 제보")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.sanction").value(nullValue()));
        assertThat(sanctionCount()).isZero();
    }

    @Test
    void 관리자_계정의_제보는_제재할_수_없다() throws Exception {
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", reporterId);
        Long reportId = report();

        sanction(reportId, "허위 제보").andExpect(status().isBadRequest());
        assertThat(reportStatus(reportId)).isEqualTo("PENDING");
    }

    @Test
    void 제재된_사용자는_점검과_대신_점검을_할_수_없다() throws Exception {
        sanction(report(), "허위 제보").andExpect(status().isOk());

        assertThat(inspectionEligibilityService.check(reporterId, drainId, null).eligible())
                .isFalse();
        assertThatThrownBy(
                () -> substituteClaimService.claim(reporterId, 999_999_999L, 37.5, 127.0))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_SANCTIONED);
    }

    @Test
    void 관리자_목록에_이_제보로_부과된_제재가_붙는다() throws Exception {
        Long sanctioned = report();
        sanction(sanctioned, "허위 제보").andExpect(status().isOk());
        Long plain = report();
        review(plain, adminToken, "{\"approved\":false,\"adminComment\":\"막힘 아님\"}")
                .andExpect(status().isOk());

        String json = mockMvc.perform(get("/api/v1/admin/reports").param("status", "REJECTED")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Integer> withSanction = JsonPath.read(json,
                "$.data.reports[?(@.reportId == " + sanctioned + ")].sanction.sanctionId");
        List<Object> withoutSanction = JsonPath.read(json,
                "$.data.reports[?(@.reportId == " + plain + ")].sanction");
        assertThat(withSanction).hasSize(1);
        assertThat(withoutSanction).containsExactly((Object) null);
    }

    private ResultActions sanction(Long reportId, String reason) throws Exception {
        return sanction(reportId, adminToken, reason);
    }

    private ResultActions sanction(Long reportId, String bearer, String reason) throws Exception {
        return review(reportId, bearer,
                "{\"approved\":false,\"sanction\":true,\"adminComment\":\"" + reason + "\"}");
    }

    private ResultActions review(Long reportId, String bearer, String body) throws Exception {
        return mockMvc.perform(patch("/api/v1/admin/reports/{reportId}", reportId)
                .header("Authorization", "Bearer " + bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    /** 처리 대기 중인 막힘 제보 */
    private Long report() {
        return jdbcTemplate.queryForObject("INSERT INTO report (user_id, drain_id, type, "
                + "description, photo_url, status, latitude, longitude, blocks_adoption, "
                + "created_at, updated_at) VALUES (?, ?, 'BLOCKAGE', '막혔어요', 'test/report/x.jpg', "
                + "'PENDING', 37.5, 127.0, false, now(), now()) RETURNING id", Long.class,
                reporterId, drainId);
    }

    private Long user(Role role) {
        User user = userRepository.save(User.local(
                UUID.randomUUID() + "@test.com", "{noop}x", role.name()));
        jdbcTemplate.update("UPDATE users SET role = ? WHERE id = ?", role.name(), user.getId());
        return user.getId();
    }

    private String token(Role role) {
        return jwtTokenProvider.createAccessToken(user(role), role);
    }

    private String reportStatus(Long reportId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM report WHERE id = ?", String.class, reportId);
    }

    private int sanctionCount() {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM sanction WHERE user_id = ?", Integer.class, reporterId);
    }

    private int notificationCount(String type) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM notification WHERE user_id = ? "
                + "AND type = ?", Integer.class, reporterId, type);
    }

    private static LocalDateTime time(String json, String path) {
        return LocalDateTime.parse(JsonPath.<String>read(json, path));
    }
}
