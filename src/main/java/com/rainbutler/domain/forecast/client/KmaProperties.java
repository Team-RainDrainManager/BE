package com.rainbutler.domain.forecast.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 기상청 단기예보 설정입니다. ({@code rain-butler.kma})
 *
 * @param authKey 기상청 API허브 인증키 (비어 있으면 수집을 건너뛴다)
 * @param baseUrl 단기예보 조회 API 주소 ({@code getVilageFcst})
 * @param nx 예보 격자 X (파일럿 역곡동 57)
 * @param ny 예보 격자 Y (파일럿 역곡동 125)
 */
@ConfigurationProperties(prefix = "rain-butler.kma")
public record KmaProperties(String authKey, String baseUrl, int nx, int ny) {

    /**
     * 인증키가 설정됐는지 확인합니다. 환경변수가 없으면 "${...}" 문자열이 들어올 수 있어 함께 막습니다.
     *
     * @return 설정 여부
     */
    public boolean isConfigured() {
        return authKey != null && !authKey.isBlank() && !authKey.startsWith("${");
    }
}
