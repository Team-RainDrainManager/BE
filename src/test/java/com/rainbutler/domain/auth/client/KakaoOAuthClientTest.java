package com.rainbutler.domain.auth.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestClient;

/**
 * 카카오 API 응답을 흉내 내 KakaoOAuthClient의 성공 · 실패 처리를 검증합니다.
 */
class KakaoOAuthClientTest {

    private static final String REDIRECT_URI = "http://localhost:5173/oauth/kakao";

    private MockRestServiceServer server;
    private KakaoOAuthClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        KakaoProperties properties = new KakaoProperties(
                "test-client-id", "test-client-secret", List.of(REDIRECT_URI));
        client = new KakaoOAuthClient(builder.build(), properties);
    }

    @Test
    void 인가_코드로_카카오_사용자_정보를_가져온다() {
        expectTokenExchange().andRespond(withSuccess(
                "{\"access_token\":\"kakao-access\",\"token_type\":\"bearer\"}",
                MediaType.APPLICATION_JSON));
        server.expect(requestTo(KakaoOAuthClient.USER_INFO_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer kakao-access"))
                .andRespond(withSuccess("""
                        {"id": 123456789,
                         "kakao_account": {"profile": {
                           "nickname": "카카오집사",
                           "profile_image_url": "https://k.kakaocdn.net/p.jpg"}}}
                        """, MediaType.APPLICATION_JSON));

        KakaoUserInfo info = client.fetchUser("auth-code", REDIRECT_URI);

        assertThat(info.kakaoId()).isEqualTo("123456789");
        assertThat(info.nickname()).isEqualTo("카카오집사");
        assertThat(info.profileImageUrl()).isEqualTo("https://k.kakaocdn.net/p.jpg");
        server.verify();
    }

    @Test
    void 닉네임_동의가_없으면_기본_닉네임을_만든다() {
        expectTokenExchange().andRespond(withSuccess(
                "{\"access_token\":\"kakao-access\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(KakaoOAuthClient.USER_INFO_URL))
                .andRespond(withSuccess("{\"id\": 1, \"kakao_account\": {}}",
                        MediaType.APPLICATION_JSON));

        KakaoUserInfo info = client.fetchUser("auth-code", REDIRECT_URI);

        assertThat(info.nickname()).matches("집사\\d{4}");
        assertThat(info.profileImageUrl()).isNull();
    }

    @Test
    void 만료되거나_재사용된_인가_코드면_KAKAO_AUTH_FAILED() {
        expectTokenExchange().andRespond(withBadRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"invalid_grant\",\"error_code\":\"KOE320\"}"));

        assertErrorCode(() -> client.fetchUser("used-code", REDIRECT_URI),
                ErrorCode.KAKAO_AUTH_FAILED);
    }

    @Test
    void 카카오_서버_오류면_KAKAO_API_ERROR() {
        expectTokenExchange().andRespond(withServerError());

        assertErrorCode(() -> client.fetchUser("auth-code", REDIRECT_URI),
                ErrorCode.KAKAO_API_ERROR);
    }

    @Test
    void 카카오_응답이_늦으면_KAKAO_API_ERROR() {
        expectTokenExchange().andRespond(withException(new SocketTimeoutException("timeout")));

        assertErrorCode(() -> client.fetchUser("auth-code", REDIRECT_URI),
                ErrorCode.KAKAO_API_ERROR);
    }

    @Test
    void 허용되지_않은_redirectUri면_카카오를_호출하지_않고_INVALID_REDIRECT_URI() {
        assertErrorCode(() -> client.fetchUser("auth-code", "https://evil.com/callback"),
                ErrorCode.INVALID_REDIRECT_URI);
        server.verify();
    }

    @Test
    void 카카오_인가_페이지_주소를_만든다() {
        String url = client.authorizeUrl(REDIRECT_URI);

        assertThat(url).startsWith(KakaoOAuthClient.AUTHORIZE_URL)
                .contains("client_id=test-client-id")
                .contains("response_type=code")
                .contains("redirect_uri=http://localhost:5173/oauth/kakao");
    }

    private ResponseActions expectTokenExchange() {
        return server.expect(requestTo(KakaoOAuthClient.TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formDataContains(Map.of(
                        "grant_type", "authorization_code",
                        "client_id", "test-client-id",
                        "client_secret", "test-client-secret",
                        "redirect_uri", REDIRECT_URI)));
    }

    private static void assertErrorCode(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(expected);
    }
}
