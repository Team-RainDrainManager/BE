package com.rainbutler.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rainbutler.domain.auth.dto.SignupRequest;
import com.rainbutler.domain.user.dto.NicknameUpdateRequest;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.domain.user.validation.ValidNickname;
import com.rainbutler.global.security.JwtTokenProvider;
import com.rainbutler.support.IntegrationTestSupport;
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
 * 닉네임 수정 API를 검증합니다.
 */
@AutoConfigureMockMvc
class NicknameUpdateApiTest extends IntegrationTestSupport {

    private static final String PATH = "/api/v1/users/me";
    private static final String NICKNAME_21 = "가나다라마바사아자차카타파하가나다라마바사";

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
        user = userRepository.save(User.local(
                UUID.randomUUID() + "@test.com", "{noop}irrelevant", "처음집사"));
        token = jwtTokenProvider.createAccessToken(user.getId(), user.getRole());
    }

    @Test
    void 닉네임을_바꾸고_id_닉네임_수정_시각을_준다() throws Exception {
        update("{\"nickname\":\"또랑이집사\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(user.getId()))
                .andExpect(jsonPath("$.data.nickname").value("또랑이집사"))
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty());

        assertThat(savedNickname()).isEqualTo("또랑이집사");
    }

    @Test
    void 앞뒤_공백을_지우고_저장한다() throws Exception {
        update("{\"nickname\":\"   비온뒤집사  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("비온뒤집사"));

        assertThat(savedNickname()).isEqualTo("비온뒤집사");
    }

    @Test
    void 공백을_지운_뒤_1자이거나_21자이면_400() throws Exception {
        update("{\"nickname\":\" 집 \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("nickname"));
        update("{\"nickname\":\"" + NICKNAME_21 + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("nickname"));
        update("{\"nickname\":\"    \"}")
                .andExpect(status().isBadRequest());

        assertThat(savedNickname()).isEqualTo("처음집사");
    }

    @Test
    void 경계값_2자와_20자는_허용한다() throws Exception {
        update("{\"nickname\":\"집사\"}").andExpect(status().isOk());
        update("{\"nickname\":\"" + NICKNAME_21.substring(0, 20) + "\"}")
                .andExpect(status().isOk());
    }

    @Test
    void 같은_닉네임으로_다시_바꿔도_200() throws Exception {
        update("{\"nickname\":\"처음집사\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("처음집사"));
    }

    @Test
    void 닉네임_외의_필드는_무시한다() throws Exception {
        update("{\"nickname\":\"새집사\",\"role\":\"ADMIN\",\"point\":999}")
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT role || ':' || point FROM users WHERE id = ?", String.class, user.getId()))
                .isEqualTo("USER:0");
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        mockMvc.perform(patch(PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"또랑이집사\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void 가입도_같은_닉네임_제약을_써서_21자를_거부한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + UUID.randomUUID() + "@test.com\","
                                + "\"password\":\"rain1234\","
                                + "\"nickname\":\"" + NICKNAME_21 + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("nickname"));

        // 두 요청이 같은 공통 제약을 쓰는지 (숫자가 따로 놀지 않게)
        assertThat(nicknameHasValidNickname(SignupRequest.class)).isTrue();
        assertThat(nicknameHasValidNickname(NicknameUpdateRequest.class)).isTrue();
    }

    private ResultActions update(String body) throws Exception {
        return mockMvc.perform(patch(PATH)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String savedNickname() {
        return jdbcTemplate.queryForObject(
                "SELECT nickname FROM users WHERE id = ?", String.class, user.getId());
    }

    private static boolean nicknameHasValidNickname(Class<?> requestType) throws Exception {
        return requestType.getDeclaredField("nickname").isAnnotationPresent(ValidNickname.class);
    }
}
