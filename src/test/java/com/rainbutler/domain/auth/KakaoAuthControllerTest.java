package com.rainbutler.domain.auth;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rainbutler.domain.auth.client.KakaoOAuthClient;
import com.rainbutler.domain.auth.client.KakaoUserInfo;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.support.IntegrationTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 카카오 로그인 엔드포인트의 신규 · 기존 사용자 분기를 검증합니다. (카카오 클라이언트는 가짜로 대체)
 */
@AutoConfigureMockMvc
class KakaoAuthControllerTest extends IntegrationTestSupport {

    private static final String REDIRECT_URI = "http://localhost:5173/oauth/kakao";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KakaoOAuthClient kakaoOAuthClient;

    @Test
    void 처음_로그인하면_가입하고_두_번째부터는_기존_사용자로_로그인한다() throws Exception {
        String kakaoId = String.valueOf(Math.abs(UUID.randomUUID().getMostSignificantBits()));
        given(kakaoOAuthClient.fetchUser(anyString(), eq(REDIRECT_URI)))
                .willReturn(new KakaoUserInfo(kakaoId, "카카오집사", null));

        kakaoLogin("code-1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewUser").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());

        kakaoLogin("code-2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewUser").value(false));
    }

    @Test
    void 카카오_인증_실패는_401_KAKAO_AUTH_FAILED() throws Exception {
        given(kakaoOAuthClient.fetchUser(anyString(), anyString()))
                .willThrow(new BusinessException(ErrorCode.KAKAO_AUTH_FAILED));

        kakaoLogin("expired-code")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("KAKAO_AUTH_FAILED"));
    }

    private ResultActions kakaoLogin(String code) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/kakao")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"redirectUri\":\"" + REDIRECT_URI + "\"}"));
    }
}
