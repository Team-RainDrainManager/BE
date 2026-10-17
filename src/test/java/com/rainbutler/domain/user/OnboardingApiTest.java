package com.rainbutler.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.domain.user.service.OnboardingGuard;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.security.JwtTokenProvider;
import com.rainbutler.support.IntegrationTestSupport;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 온보딩 API와 가드를 실제 DB로 검증합니다.
 */
@AutoConfigureMockMvc
class OnboardingApiTest extends IntegrationTestSupport {

    private static final String PATH = "/api/v1/users/me/onboarding";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private OnboardingGuard onboardingGuard;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 약관 종류 → 현재 약관 id (GET /api/v1/terms에서 읽는다) */
    private Map<String, Long> termsIds;
    private User user;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        String body = mockMvc.perform(get("/api/v1/terms")).andReturn().getResponse()
                .getContentAsString();
        List<String> types = JsonPath.read(body, "$.data[*].type");
        List<Integer> ids = JsonPath.read(body, "$.data[*].termsId");
        termsIds = new LinkedHashMap<>();
        for (int i = 0; i < types.size(); i++) {
            termsIds.put(types.get(i), ids.get(i).longValue());
        }
        user = userRepository.save(User.local(
                UUID.randomUUID() + "@test.com", "{noop}irrelevant", "온보딩집사"));
        token = jwtTokenProvider.createAccessToken(user.getId(), user.getRole());
    }

    @Test
    void 온보딩_전에는_미완료이고_필수_약관_5개가_대기_중이다() throws Exception {
        mockMvc.perform(get(PATH).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(false))
                .andExpect(jsonPath("$.data.identityVerified").value(false))
                .andExpect(jsonPath("$.data.pendingRequiredTerms.length()").value(5));
    }

    @Test
    void 모두_동의하면_동의_7행_인증_시각_알림_설정_완료_시각을_남긴다() throws Exception {
        complete(request(Set.of(), true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(true))
                .andExpect(jsonPath("$.data.identityVerified").value(true))
                .andExpect(jsonPath("$.data.pendingRequiredTerms").isEmpty());

        Map<String, Object> row = userRow();
        assertThat(row.get("identity_verified_at")).isNotNull();
        assertThat(row.get("onboarding_completed_at")).isNotNull();
        assertThat(row.get("notification_enabled")).isEqualTo(true);
        assertThat(row.get("night_push_consent")).isEqualTo(true);
        // 본인인증은 목업이라 실명 등 개인정보를 저장하지 않는다
        assertThat(row.get("real_name")).isNull();
        assertThat(agreementCount(true)).isEqualTo(7);

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.onboardingCompleted").value(true))
                .andExpect(jsonPath("$.data.identityVerified").value(true));
    }

    @Test
    void 선택_약관에_동의하지_않으면_알림_설정이_꺼지고_미동의도_이력에_남는다() throws Exception {
        complete(request(Set.of("NOTIFICATION", "NIGHT_PUSH"), true))
                .andExpect(status().isOk());

        Map<String, Object> row = userRow();
        assertThat(row.get("notification_enabled")).isEqualTo(false);
        assertThat(row.get("night_push_consent")).isEqualTo(false);
        assertThat(agreementCount(true)).isEqualTo(5);
        assertThat(agreementCount(false)).isEqualTo(2);
    }

    @Test
    void 필수_약관에_동의하지_않으면_REQUIRED_TERMS_NOT_AGREED() throws Exception {
        complete(request(Set.of("SAFETY"), true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("REQUIRED_TERMS_NOT_AGREED"));
        assertNothingSaved();
    }

    @Test
    void 약관_응답이_빠지면_INVALID_INPUT() throws Exception {
        List<Map<String, Object>> agreements = agreements(Set.of());
        agreements.removeIf(item -> item.get("termsId").equals(termsIds.get("PRIVACY")));

        complete(json(agreements, true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
        assertNothingSaved();
    }

    @Test
    void 옛_버전_약관으로_동의하면_TERMS_OUTDATED() throws Exception {
        Long serviceV2 = jdbcTemplate.queryForObject("INSERT INTO terms (type, version, title, "
                + "required, content_path, display_order, effective_at, created_at, updated_at) "
                + "VALUES ('SERVICE', 2, '서비스 이용약관에 동의합니다', true, "
                + "'terms/SERVICE_v1.md', 2, now() - interval '1 minute', now(), now()) "
                + "RETURNING id", Long.class);
        try {
            // 화면을 v2 시행 전에 받아 SERVICE v1 id로 보낸 경우
            complete(request(Set.of(), true))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("TERMS_OUTDATED"));
            assertNothingSaved();
        } finally {
            jdbcTemplate.update("DELETE FROM terms WHERE id = ?", serviceV2);
        }
    }

    @Test
    void 본인인증을_하지_않으면_IDENTITY_VERIFICATION_REQUIRED_ON_ONBOARDING() throws Exception {
        complete(request(Set.of(), false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code")
                        .value("IDENTITY_VERIFICATION_REQUIRED_ON_ONBOARDING"));
        complete(json(agreements(Set.of()), null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code")
                        .value("IDENTITY_VERIFICATION_REQUIRED_ON_ONBOARDING"));
        assertNothingSaved();
    }

    @Test
    void 이미_마친_사용자가_다시_호출하면_409() throws Exception {
        complete(request(Set.of(), true)).andExpect(status().isOk());

        complete(request(Set.of(), true))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ONBOARDING_ALREADY_COMPLETED"));
        assertThat(agreementCount(true)).isEqualTo(7);
    }

    @Test
    void 가드는_미완료면_403_ONBOARDING_REQUIRED_완료면_통과() throws Exception {
        assertThatThrownBy(() -> onboardingGuard.require(user.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ONBOARDING_REQUIRED);
        assertThat(ErrorCode.ONBOARDING_REQUIRED.getStatus().value()).isEqualTo(403);

        complete(request(Set.of(), true)).andExpect(status().isOk());

        assertThatCode(() -> onboardingGuard.require(user.getId())).doesNotThrowAnyException();
    }

    private ResultActions complete(String body) throws Exception {
        return mockMvc.perform(post(PATH)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    /** 현재 약관 전부에 대해, rejectedTypes만 미동의로 만든 요청 */
    private String request(Set<String> rejectedTypes, Boolean identityVerified) {
        return json(agreements(rejectedTypes), identityVerified);
    }

    private List<Map<String, Object>> agreements(Set<String> rejectedTypes) {
        List<Map<String, Object>> agreements = new ArrayList<>();
        termsIds.forEach((type, id) -> agreements.add(
                Map.of("termsId", id, "agreed", !rejectedTypes.contains(type))));
        return agreements;
    }

    private static String json(List<Map<String, Object>> agreements, Boolean identityVerified) {
        StringBuilder items = new StringBuilder();
        for (Map<String, Object> item : agreements) {
            if (!items.isEmpty()) {
                items.append(',');
            }
            items.append("{\"termsId\":").append(item.get("termsId"))
                    .append(",\"agreed\":").append(item.get("agreed")).append('}');
        }
        String identity =
                identityVerified == null ? "" : ",\"identityVerified\":" + identityVerified;
        return "{\"agreements\":[" + items + "]" + identity + "}";
    }

    private Map<String, Object> userRow() {
        return jdbcTemplate.queryForMap("SELECT identity_verified_at, onboarding_completed_at, "
                + "notification_enabled, night_push_consent, real_name FROM users WHERE id = ?",
                user.getId());
    }

    private int agreementCount(boolean agreed) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM user_terms_agreement "
                + "WHERE user_id = ? AND agreed = ?", Integer.class, user.getId(), agreed);
    }

    private void assertNothingSaved() {
        assertThat(agreementCount(true) + agreementCount(false)).isZero();
        assertThat(userRow().get("onboarding_completed_at")).isNull();
        assertThat(userRow().get("identity_verified_at")).isNull();
    }
}
