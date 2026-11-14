package com.rainbutler.domain.weather.client;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 지도 배너를 조회할 때 그 격자 현재 날씨를 기상청에서 바로 받아 오는 설정입니다.
 * ({@code rain-butler.kma.on-demand})
 *
 * <p>배너는 지도 화면마다 호출되므로 응답은 짧게 끊고, 기상청 호출은 뒤에서 끝까지 받아 저장한다.
 *
 * @param responseTimeout 배너 응답이 기상청을 기다리는 최대 시간 (넘으면 저장된 값으로 응답, 호출은 계속)
 * @param fetchTimeout 백그라운드 기상청 호출 최대 시간 (재시도 없음, 넘으면 실패로 보고 {@code failureBackoff} 쉬기)
 * @param refetchInterval 마지막 호출 뒤 이 시간 안에는 실황 시각이 옛날이어도 다시 부르지 않는다
 *     (기상청이 아직 새 값을 내지 않았을 수 있음)
 * @param maxPerMinute 서버 전체 분당 호출 상한 (로그인 없는 공개 API라 지도를 마구 움직여도 기상청 호출이 몰리지 않게)
 * @param failureBackoff 실패 · 시간 초과한 격자를 다시 부르지 않는 시간 (기상청 장애 때 반복 호출 방지)
 */
@ConfigurationProperties(prefix = "rain-butler.kma.on-demand")
public record WeatherOnDemandProperties(Duration responseTimeout, Duration fetchTimeout,
        Duration refetchInterval,
        int maxPerMinute, Duration failureBackoff) {
}
