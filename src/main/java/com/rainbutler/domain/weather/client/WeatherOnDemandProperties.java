package com.rainbutler.domain.weather.client;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 지도 배너를 조회할 때 그 격자 현재 날씨를 기상청에서 바로 받아 오는 설정입니다.
 * ({@code rain-butler.kma.on-demand})
 *
 * <p>배너는 지도 화면마다 호출되므로 수집 배치(타임아웃 5초 · 10초, 재시도 2회)와 따로 짧게 잡는다.
 *
 * @param timeout 기상청 호출 타임아웃 · 응답 대기 상한 (재시도 없음)
 * @param refetchInterval 마지막 호출 뒤 이 시간 안에는 실황 시각이 옛날이어도 다시 부르지 않는다
 *     (기상청이 아직 새 값을 내지 않았을 수 있음)
 * @param maxPerMinute 서버 전체 분당 호출 상한 (로그인 없는 공개 API라 지도를 마구 움직여도 기상청 호출이 몰리지 않게)
 * @param failureBackoff 실패 · 시간 초과한 격자를 다시 부르지 않는 시간 (기상청 장애 때 반복 호출 방지)
 */
@ConfigurationProperties(prefix = "rain-butler.kma.on-demand")
public record WeatherOnDemandProperties(Duration timeout, Duration refetchInterval,
        int maxPerMinute, Duration failureBackoff) {
}
