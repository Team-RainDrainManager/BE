package com.rainbutler.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.global.security.JwtTokenProvider;
import com.rainbutler.support.IntegrationTestSupport;
import java.time.LocalDateTime;
import java.util.List;
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
 * 알림 설정 조회 · 변경 API를 검증합니다.
 */
@AutoConfigureMockMvc
class UserSettingsApiTest extends IntegrationTestSupport {

    private static final String PATH = "/api/v1/users/me/settings";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User user;
    private String token;

    @BeforeEach
    void setUp() {
        // 온보딩에서 활동 알림만 동의하고 야간 푸시는 동의하지 않은 사용자
        User onboarded = User.local(UUID.randomUUID() + "@test.com", "{noop}x", "설정집사");
        onboarded.completeOnboarding(LocalDateTime.now(), true, false);
        user = userRepository.save(onboarded);
        token = jwtTokenProvider.createAccessToken(user.getId(), user.getRole());
    }

    @Test
    void 조회하면_온보딩에서_고른_값이_나온다() throws Exception {
        mockMvc.perform(get(PATH).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notifications").value(true))
                .andExpect(jsonPath("$.data.nightConsent").value(false));
    }

    @Test
    void 보낸_값만_바꾸고_나머지는_유지한다() throws Exception {
        update("{\"nightConsent\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notifications").value(true))
                .andExpect(jsonPath("$.data.nightConsent").value(true));

        update("{\"notifications\":false,\"nightConsent\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notifications").value(false))
                .andExpect(jsonPath("$.data.nightConsent").value(true));
    }

    @Test
    void 둘_다_없으면_400() throws Exception {
        update("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.error.message").value("변경할 설정을 보내 주세요."));
        update("{\"notifications\":null,\"nightConsent\":null}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void 값이_바뀔_때만_동의_철회_이력이_쌓인다() throws Exception {
        update("{\"notifications\":true}").andExpect(status().isOk());
        assertThat(history()).isEmpty();

        update("{\"nightConsent\":true}").andExpect(status().isOk());
        update("{\"notifications\":false,\"nightConsent\":true}").andExpect(status().isOk());

        assertThat(history()).containsExactly("NIGHT_PUSH=true", "NOTIFICATION=false");
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mockMvc.perform(patch(PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notifications\":true}"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions update(String body) throws Exception {
        return mockMvc.perform(patch(PATH)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    /** 이 사용자의 알림 설정 동의 이력 (추가 순서) */
    private List<String> history() {
        return jdbcTemplate.queryForList("SELECT t.type || '=' || a.agreed "
                + "FROM user_terms_agreement a JOIN terms t ON t.id = a.terms_id "
                + "WHERE a.user_id = ? ORDER BY a.id",
                String.class, user.getId());
    }
}
