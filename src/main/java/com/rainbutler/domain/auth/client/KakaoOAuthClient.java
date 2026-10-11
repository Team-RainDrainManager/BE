package com.rainbutler.domain.auth.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import java.security.SecureRandom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 카카오 OAuth(인가 코드 방식) 클라이언트입니다.
 *
 * <p>인가 코드를 카카오 access 토큰으로 바꾸고, 사용자 정보를 조회합니다. 카카오 토큰은 저장하지 않습니다.
 * <ul>
 *   <li>허용 목록에 없는 redirectUri → {@code INVALID_REDIRECT_URI}</li>
 *   <li>카카오 4xx (invalid_grant 등) → {@code KAKAO_AUTH_FAILED}</li>
 *   <li>카카오 5xx · 타임아웃 · 키 미설정 → {@code KAKAO_API_ERROR}</li>
 * </ul>
 */
@Slf4j
@Component
public class KakaoOAuthClient {

    static final String AUTHORIZE_URL = "https://kauth.kakao.com/oauth/authorize";
    static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    static final String USER_INFO_URL = "https://kapi.kakao.com/v2/user/me";

    private static final String DEFAULT_NICKNAME_PREFIX = "집사";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RestClient restClient;
    private final KakaoProperties properties;

    public KakaoOAuthClient(@Qualifier("kakaoRestClient") RestClient restClient,
            KakaoProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
        if (!properties.isConfigured()) {
            log.warn("카카오 키(KAKAO_CLIENT_ID, KAKAO_CLIENT_SECRET)가 설정되지 않아 카카오 로그인을 쓸 수 없습니다.");
        }
    }

    /**
     * 카카오 인가 페이지 주소를 만듭니다. (프론트 · 로컬 테스트용)
     *
     * @param redirectUri 카카오가 인가 코드를 돌려줄 주소
     * @return 카카오 인가 페이지 URL
     */
    public String authorizeUrl(String redirectUri) {
        validateRedirectUri(redirectUri);
        requireConfigured();
        return UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
                .queryParam("response_type", "code")
                .queryParam("client_id", properties.clientId())
                .queryParam("redirect_uri", redirectUri)
                .encode()
                .toUriString();
    }

    /**
     * 인가 코드로 카카오 사용자 정보를 가져옵니다.
     *
     * @param code 카카오 인가 코드
     * @param redirectUri 인가 요청 때 쓴 redirectUri
     * @return 카카오 사용자 정보
     */
    public KakaoUserInfo fetchUser(String code, String redirectUri) {
        validateRedirectUri(redirectUri);
        requireConfigured();
        String kakaoAccessToken = exchangeToken(code, redirectUri);
        return getUserInfo(kakaoAccessToken);
    }

    /**
     * redirectUri가 허용 목록과 정확히 일치하는지 확인합니다.
     */
    private void validateRedirectUri(String redirectUri) {
        if (redirectUri == null || properties.allowedRedirectUris() == null
                || !properties.allowedRedirectUris().contains(redirectUri)) {
            throw new BusinessException(ErrorCode.INVALID_REDIRECT_URI);
        }
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            log.error("카카오 키가 설정되지 않았습니다. KAKAO_CLIENT_ID, KAKAO_CLIENT_SECRET을 확인하세요.");
            throw new BusinessException(ErrorCode.KAKAO_API_ERROR);
        }
    }

    /**
     * 인가 코드를 카카오 access 토큰으로 교환합니다.
     */
    private String exchangeToken(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("redirect_uri", redirectUri);
        form.add("code", code);

        KakaoTokenResponse response = call("토큰 교환", () -> restClient.post()
                .uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(KakaoTokenResponse.class));
        if (response == null || response.accessToken() == null) {
            throw new BusinessException(ErrorCode.KAKAO_API_ERROR);
        }
        return response.accessToken();
    }

    /**
     * 카카오 access 토큰으로 사용자 정보를 조회합니다.
     */
    private KakaoUserInfo getUserInfo(String kakaoAccessToken) {
        KakaoUserResponse response = call("사용자 정보 조회", () -> restClient.get()
                .uri(USER_INFO_URL)
                .header("Authorization", "Bearer " + kakaoAccessToken)
                .retrieve()
                .body(KakaoUserResponse.class));
        if (response == null || response.id() == null) {
            throw new BusinessException(ErrorCode.KAKAO_API_ERROR);
        }

        KakaoUserResponse.Profile profile = response.kakaoAccount() == null
                ? null : response.kakaoAccount().profile();
        String nickname = profile == null || profile.nickname() == null
                || profile.nickname().isBlank()
                ? defaultNickname() : profile.nickname().strip();
        String profileImageUrl = profile == null ? null : profile.profileImageUrl();
        return new KakaoUserInfo(String.valueOf(response.id()), nickname, profileImageUrl);
    }

    /**
     * 카카오 호출 오류를 에러 코드로 바꿉니다.
     */
    private <T> T call(String action, KakaoCall<T> call) {
        try {
            return call.execute();
        } catch (HttpClientErrorException e) {
            // 만료 · 재사용된 인가 코드(invalid_grant), redirectUri 불일치 등
            log.warn("[카카오 {} 실패] {} {}", action, e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.KAKAO_AUTH_FAILED);
        } catch (RestClientException e) {
            // 5xx, 타임아웃, 연결 실패, 응답 해석 실패
            log.error("[카카오 {} 오류] {}", action, e.getMessage(), e);
            throw new BusinessException(ErrorCode.KAKAO_API_ERROR);
        }
    }

    /**
     * 닉네임 동의를 받지 못했을 때 쓰는 기본 닉네임입니다. (예: 집사0427)
     */
    private static String defaultNickname() {
        return DEFAULT_NICKNAME_PREFIX + String.format("%04d", RANDOM.nextInt(10_000));
    }

    @FunctionalInterface
    private interface KakaoCall<T> {
        T execute();
    }

    /** 카카오 토큰 응답 (필요한 값만) */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record KakaoTokenResponse(@JsonProperty("access_token") String accessToken) {
    }

    /** 카카오 사용자 정보 응답 (필요한 값만) */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record KakaoUserResponse(
            Long id,
            @JsonProperty("kakao_account") KakaoAccount kakaoAccount) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record KakaoAccount(Profile profile) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Profile(
                String nickname,
                @JsonProperty("profile_image_url") String profileImageUrl) {
        }
    }
}
