package com.rainbutler.domain.forecast.client;

import com.rainbutler.global.geo.KmaGridConverter.Grid;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 기상청 단기예보 설정입니다. ({@code rain-butler.kma})
 *
 * @param authKey 기상청 API허브 인증키 (비어 있으면 수집을 건너뛴다)
 * @param baseUrl 단기예보 조회 API 주소 ({@code getVilageFcst})
 * @param ultraSrtNcstUrl 초단기실황 조회 API 주소 ({@code getUltraSrtNcst}, 지금 비가 오는지)
 * @param defaultGridNx 기본 격자 X (파일럿 역곡동 57). 격자 없는 빗물받이 · 좌표 없는 배너 · 시연 기본값
 * @param defaultGridNy 기본 격자 Y (파일럿 역곡동 125)
 * @param maxGridsPerRun 한 번 수집에서 호출할 최대 격자 수 (넘으면 입양된 빗물받이가 있는 격자 우선)
 */
@ConfigurationProperties(prefix = "rain-butler.kma")
public record KmaProperties(
        String authKey, String baseUrl, String ultraSrtNcstUrl, int defaultGridNx,
        int defaultGridNy, int maxGridsPerRun) {

    /**
     * 기본 격자입니다.
     *
     * @return 기본 격자
     */
    public Grid defaultGrid() {
        return new Grid(defaultGridNx, defaultGridNy);
    }

    /**
     * 인증키가 설정됐는지 확인합니다. 환경변수가 없으면 "${...}" 문자열이 들어올 수 있어 함께 막습니다.
     *
     * @return 설정 여부
     */
    public boolean isConfigured() {
        return authKey != null && !authKey.isBlank() && !authKey.startsWith("${");
    }
}
