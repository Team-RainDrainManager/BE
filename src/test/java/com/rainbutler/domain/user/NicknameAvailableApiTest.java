package com.rainbutler.domain.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.global.security.JwtTokenProvider;
import com.rainbutler.support.IntegrationTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class NicknameAvailableApiTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private Long userId;
    private String token;
    private String uniqueNickname;

    @BeforeEach
    void setUp() {
        uniqueNickname = "테스트유저" + UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.save(User.local(
                UUID.randomUUID() + "@test.com", "{noop}x", uniqueNickname));
        userId = user.getId();
        token = jwtTokenProvider.createAccessToken(userId, user.getRole());
    }

    @Test
    void 이미_쓰는_닉네임은_사용_불가로_나온다() throws Exception {
        mockMvc.perform(get("/api/v1/users/nickname/available")
                        .param("nickname", uniqueNickname))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(false));
    }

    @Test
    void 대소문자만_다른_닉네임도_사용_불가로_나온다() throws Exception {
        mockMvc.perform(get("/api/v1/users/nickname/available")
                        .param("nickname", uniqueNickname.toUpperCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(false));
    }

    @Test
    void 사용_안_하는_닉네임은_사용_가능하다() throws Exception {
        mockMvc.perform(get("/api/v1/users/nickname/available")
                        .param("nickname", "사용안하는닉네임"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    void 앞뒤_공백이_붙어도_같은_닉네임은_사용_불가로_나온다() throws Exception {
        mockMvc.perform(get("/api/v1/users/nickname/available")
                        .param("nickname", " " + uniqueNickname + " "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(false));
    }

    @Test
    void 두_글자보다_짧으면_사용_가능으로_답하지_않는다() throws Exception {
        mockMvc.perform(get("/api/v1/users/nickname/available")
                        .param("nickname", "가"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("nickname"));
    }

    @Test
    void 빈_닉네임은_사용_가능으로_답하지_않는다() throws Exception {
        mockMvc.perform(get("/api/v1/users/nickname/available")
                        .param("nickname", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("nickname"));
    }

    @Test
    void 스무_글자를_넘으면_사용_가능으로_답하지_않는다() throws Exception {
        mockMvc.perform(get("/api/v1/users/nickname/available")
                        .param("nickname", "가".repeat(21)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("nickname"));
    }
}
