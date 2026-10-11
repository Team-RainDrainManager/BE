package com.rainbutler.domain.auth;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.rainbutler.domain.user.entity.Role;
import com.rainbutler.global.security.JwtTokenProvider;
import com.rainbutler.support.IntegrationTestSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 자체 로그인 · 토큰 · 인가 흐름을 실제 보안 필터와 DB로 검증합니다.
 */
@AutoConfigureMockMvc
class AuthControllerTest extends IntegrationTestSupport {

    private static final String PASSWORD = "rain1234";
    private static final String ADMIN_PATH = "/api/v1/admin/test-only";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void 회원가입_로그인_후_내_정보를_조회한다() throws Exception {
        String email = uniqueEmail();
        signup(email, PASSWORD, "빗물집사")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));

        String accessToken = accessTokenOf(login(email, PASSWORD).andExpect(status().isOk()));

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.nickname").value("빗물집사"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.provider").value("LOCAL"));
    }

    @Test
    void 이메일은_대소문자를_구분하지_않고_중복_가입하면_409() throws Exception {
        String email = uniqueEmail();
        signup(email, PASSWORD, "집사하나").andExpect(status().isCreated());

        signup(email.toUpperCase(), PASSWORD, "집사둘")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void 비밀번호가_틀리거나_없는_이메일이면_같은_401() throws Exception {
        String email = uniqueEmail();
        signup(email, PASSWORD, "집사").andExpect(status().isCreated());

        login(email, "wrong1234")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
        login(uniqueEmail(), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void 가입_입력값이_잘못되면_400과_fieldErrors() throws Exception {
        signup("not-an-email", "short", " a ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.error.fieldErrors[*].field", hasItem("email")))
                .andExpect(jsonPath("$.error.fieldErrors[*].field", hasItem("password")))
                .andExpect(jsonPath("$.error.fieldErrors[*].field", hasItem("nickname")));
    }

    @Test
    void 토큰_없이_보호된_API를_호출하면_401_UNAUTHORIZED() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void 만료된_토큰이면_401_EXPIRED_TOKEN() throws Exception {
        // 기본 허용 오차(60초)를 넘기도록 30분 전에 이미 만료된 토큰을 만든다
        Instant issuedAt = Instant.now().minus(Duration.ofHours(1));
        String expired = jwtTokenProvider.createAccessToken(1L, Role.USER, issuedAt);

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("EXPIRED_TOKEN"));
    }

    @Test
    void 형식이_잘못된_토큰이면_401_INVALID_TOKEN() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }

    @Test
    void USER가_관리자_API를_호출하면_403_FORBIDDEN() throws Exception {
        String userToken = jwtTokenProvider.createAccessToken(1L, Role.USER);
        String adminToken = jwtTokenProvider.createAccessToken(1L, Role.ADMIN);

        mockMvc.perform(get(ADMIN_PATH).header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mockMvc.perform(get(ADMIN_PATH).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void 지도_목록은_로그인_없이_조회할_수_있다() throws Exception {
        mockMvc.perform(get("/api/drain")
                        .param("minLatitude", "37").param("maxLatitude", "38")
                        .param("minLongitude", "126").param("maxLongitude", "127"))
                .andExpect(status().isOk());
    }

    @Test
    void refresh를_회전하고_로그아웃하면_그_refresh는_쓸_수_없다() throws Exception {
        String email = uniqueEmail();
        String body = signup(email, PASSWORD, "집사").andReturn().getResponse().getContentAsString();
        String refreshToken = JsonPath.read(body, "$.data.refreshToken");

        // 회전: 새 토큰을 받고, 이전 refresh는 더 이상 못 쓴다
        String rotated = refresh(refreshToken).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String newAccess = JsonPath.read(rotated, "$.data.accessToken");
        String newRefresh = JsonPath.read(rotated, "$.data.refreshToken");

        // 로그아웃: 새 refresh 폐기
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + newAccess)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + newRefresh + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        refresh(newRefresh)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void 로그아웃은_토큰이_없으면_401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    private ResultActions signup(String email, String password, String nickname)
            throws Exception {
        return mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password
                        + "\",\"nickname\":\"" + nickname + "\"}"));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"));
    }

    private static String accessTokenOf(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(),
                "$.data.accessToken");
    }

    private static String uniqueEmail() {
        return "u" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
    }
}
