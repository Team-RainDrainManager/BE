package com.rainbutler.domain.weather.service;

import com.rainbutler.domain.forecast.client.KmaBaseTime;
import com.rainbutler.domain.weather.client.WeatherOnDemandProperties;
import com.rainbutler.domain.weather.repository.CurrentWeatherRepository;
import com.rainbutler.domain.weather.repository.CurrentWeatherRepository.CurrentWeather;
import com.rainbutler.global.geo.KmaGridConverter.Grid;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 격자마다 지금 비가 오는지 판단합니다. (지도 비 예보 배너)
 *
 * <ul>
 *   <li>시연용 강제 비 종료 시각 전이면 비 오는 중</li>
 *   <li>아니면 초단기실황이 비(강수형태 1 · 2 · 5 · 6)인지. 단, 실황 기준 시각이 3시간보다 오래됐으면 수집이 멈춘
 *       것으로 보고 "모름" → 비 아님으로 처리한다 (오래된 비 표시 방지)</li>
 * </ul>
 * 그 격자 기록이 없거나, 기상청이 지금 낼 수 있는 가장 최근 실황보다 오래됐으면 판단 전에 기상청에서 바로 받아 온다
 * ({@link OnDemandWeatherFetcher}). 수집 배치가 빗물받이 있는 격자만 받아서, 빗물받이 없는 지역도 비를 알 수 있게
 * 하려는 것이다. 단, 마지막 호출 뒤 {@code refetch-interval}(10분) 안이면 다시 부르지 않는다.
 */
@Service
@RequiredArgsConstructor
public class CurrentWeatherService {

    /** 이보다 오래된 실황은 믿지 않는다 */
    private static final Duration FRESHNESS = Duration.ofHours(3);

    private final CurrentWeatherRepository currentWeatherRepository;
    private final OnDemandWeatherFetcher onDemandWeatherFetcher;
    private final WeatherOnDemandProperties onDemandProperties;

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
     * 격자에 지금 비가 오는지 판단합니다.
     *
     * @param grid 기상청 격자
     * @param now 현재 시각
     * @return 날씨 상태
     */
    public WeatherStatus status(Grid grid, LocalDateTime now) {
        Optional<CurrentWeather> current = currentWeatherRepository.find(grid);
        if (current.map(weather -> isSimulatedRaining(weather, now)).orElse(false)) {
            // 시연용 강제 비는 기상청을 부르지 않는다
            return new WeatherStatus(true, current.get().observedAt(), true);
        }
        if (needsRefresh(current, now)) {
            onDemandWeatherFetcher.refresh(grid, now);
            current = currentWeatherRepository.find(grid);
        }
        if (current.isEmpty()) {
            return new WeatherStatus(false, null, false);
        }
        CurrentWeather weather = current.get();
        boolean fresh = weather.observedAt() != null
                && !weather.observedAt().isBefore(now.minus(FRESHNESS));
        return new WeatherStatus(fresh && weather.raining(), weather.observedAt(), false);
    }

    /**
     * 기상청에서 다시 받아 와야 하는지 판단합니다.
     *
     * <ul>
     *   <li>기록이 없으면 받는다</li>
     *   <li>실황 기준 시각이 지금 낼 수 있는 가장 최근 실황(정시 발표, 40분 뒤부터)과 같으면 받지 않는다</li>
     *   <li>더 옛날이어도 마지막 호출이 {@code refetch-interval} 안이면 받지 않는다 (아직 새 값이 안 나왔을 수 있음)</li>
     * </ul>
     */
    private boolean needsRefresh(Optional<CurrentWeather> current, LocalDateTime now) {
        if (current.isEmpty()) {
            return true;
        }
        CurrentWeather weather = current.get();
        if (KmaBaseTime.latestObservation(now).equals(weather.observedAt())) {
            return false;
        }
        return weather.fetchedAt() == null
                || weather.fetchedAt().isBefore(now.minus(onDemandProperties.refetchInterval()));
    }

    private static boolean isSimulatedRaining(CurrentWeather weather, LocalDateTime now) {
        return weather.simulatedRainingUntil() != null
                && now.isBefore(weather.simulatedRainingUntil());
    }

    /**
     * 시연용으로 격자를 지금부터 정한 시간 동안 "비 오는 중"으로 표시합니다. 0분이면 해제합니다.
     *
     * @param grid 기상청 격자
     * @param minutes 분 (0이면 해제)
     * @param now 현재 시각
     * @return 강제 비 종료 시각 (해제했으면 null)
     */
    public LocalDateTime simulateRain(Grid grid, int minutes, LocalDateTime now) {
        LocalDateTime until = minutes == 0 ? null : now.plusMinutes(minutes);
        currentWeatherRepository.saveSimulatedRainingUntil(grid, until, now);
        return until;
    }
}
