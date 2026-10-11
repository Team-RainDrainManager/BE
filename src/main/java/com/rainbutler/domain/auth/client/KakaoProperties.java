package com.rainbutler.domain.auth.client;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 카카오 로그인 설정입니다. ({@code rain-butler.kakao})
 *
 * @param clientId 카카오 REST API 키
 * @param clientSecret 카카오 Client Secret
 * @param allowedRedirectUris 허용할 redirectUri 목록 (카카오 디벨로퍼스 등록 값과 같아야 함)
 */
@ConfigurationProperties(prefix = "rain-butler.kakao")
public record KakaoProperties(
        String clientId,
        String clientSecret,
        List<String> allowedRedirectUris) {

    /**
     * 키가 모두 설정됐는지 확인합니다. 환경변수가 없으면 "${...}" 문자열이 들어올 수 있어 함께 막습니다.
     *
     * @return 설정 여부
     */
    public boolean isConfigured() {
        return hasValue(clientId) && hasValue(clientSecret);
    }

    private static boolean hasValue(String value) {
        return value != null && !value.isBlank() && !value.startsWith("${");
    }
}
