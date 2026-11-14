package com.rainbutler.domain.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    void 사용가_닉네임은_중복으로_나온다() throws Exception {
        mockMvc.perform(get("/api/v1/users/nickname/available")
                        .param("nickname", uniqueNickname))
                .andExpect(status().isOk());
    }

    @Test
    void 사용_안_하는_닉네임은_사용_가능하다() throws Exception {
        mockMvc.perform(get("/api/v1/users/nickname/available")
                        .param("nickname", "사용안하는닉네임"))
                .andExpect(status().isOk());
    }
}
