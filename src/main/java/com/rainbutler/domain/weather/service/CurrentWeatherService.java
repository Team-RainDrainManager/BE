package com.rainbutler.domain.weather.service;

import com.rainbutler.domain.weather.repository.CurrentWeatherRepository;
import com.rainbutler.domain.weather.repository.CurrentWeatherRepository.CurrentWeather;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 지금 비가 오는지 판단합니다. (지도 비 예보 배너)
 *
 * <ul>
 *   <li>시연용 강제 비 종료 시각 전이면 비 오는 중</li>
 *   <li>아니면 초단기실황이 비(강수형태 1 · 2 · 5 · 6)인지. 단, 실황 기준 시각이 3시간보다 오래됐으면 수집이 멈춘
 *       것으로 보고 "모름" → 비 아님으로 처리한다 (오래된 비 표시 방지)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CurrentWeatherService {

    /** 이보다 오래된 실황은 믿지 않는다 */
    private static final Duration FRESHNESS = Duration.ofHours(3);

    private final CurrentWeatherRepository currentWeatherRepository;

    /**
     * 지금 날씨 상태입니다.
     *
     * @param raining 비 오는 중
     * @param observedAt 판단에 쓴 실황 기준 시각 (없으면 null)
     * @param simulated 시연용 강제 비로 판단했는지
     */
    public record WeatherStatus(boolean raining, LocalDateTime observedAt, boolean simulated) {
    }

    /**
     * 지금 비가 오는지 판단합니다.
     *
     * @param now 현재 시각
     * @return 날씨 상태
     */
    public WeatherStatus status(LocalDateTime now) {
        Optional<CurrentWeather> current = currentWeatherRepository.find();
        if (current.isEmpty()) {
            return new WeatherStatus(false, null, false);
        }
        CurrentWeather weather = current.get();
        if (weather.simulatedRainingUntil() != null
                && now.isBefore(weather.simulatedRainingUntil())) {
            return new WeatherStatus(true, weather.observedAt(), true);
        }
        boolean fresh = weather.observedAt() != null
                && !weather.observedAt().isBefore(now.minus(FRESHNESS));
        return new WeatherStatus(fresh && weather.raining(), weather.observedAt(), false);
    }
}
