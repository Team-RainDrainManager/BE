package com.rainbutler.global.storage;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rainbutler.domain.user.entity.Role;
import com.rainbutler.global.security.JwtTokenProvider;
import com.rainbutler.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 사진 업로드 URL 발급 API를 검증합니다. (테스트 프로필의 가짜 버킷 · 키로 presign만 하고 AWS는 호출하지 않음)
 */
@AutoConfigureMockMvc
class UploadControllerTest extends IntegrationTestSupport {

    private static final String PATH = "/api/v1/uploads/presigned-url";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void 로그인하지_않으면_401() throws Exception {
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"DRAIN\",\"contentType\":\"image/jpeg\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void 없는_용도면_400_INVALID_INPUT() throws Exception {
        request("{\"purpose\":\"SELFIE\",\"contentType\":\"image/jpeg\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void 사진이_아닌_타입이면_400_UNSUPPORTED_IMAGE_TYPE() throws Exception {
        request("{\"purpose\":\"DRAIN\",\"contentType\":\"application/pdf\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_IMAGE_TYPE"));
    }

    @Test
    void 정상이면_key와_업로드_URL과_헤더를_준다() throws Exception {
        request("{\"purpose\":\"REPORT\",\"contentType\":\"image/webp\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.key").value(
                        matchesPattern("test/report/7/[0-9a-f-]{36}\\.webp")))
                .andExpect(jsonPath("$.data.uploadUrl").value(
                        startsWith("https://test-bucket.s3.")))
                .andExpect(jsonPath("$.data.headers.Content-Type").value("image/webp"))
                .andExpect(jsonPath("$.data.expiresAt").isNotEmpty());
    }

    private ResultActions request(String body) throws Exception {
        String token = jwtTokenProvider.createAccessToken(7L, Role.USER);
        return mockMvc.perform(post(PATH)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
