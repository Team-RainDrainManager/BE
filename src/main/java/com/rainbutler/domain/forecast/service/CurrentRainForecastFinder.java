package com.rainbutler.domain.forecast.service;

import com.rainbutler.domain.forecast.entity.RainForecast;
import com.rainbutler.domain.forecast.repository.RainForecastRepository;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 지도에 보여 줄 "이번 비" 예보를 고릅니다. (비 예보 배너 · 지도 빗물받이 대신 점검 상태가 같은 기준을 쓴다)
 *
 * <ul>
 *   <li>진행 중(ACTIVE) 예보 중 비 오는 날이 오늘 또는 내일인 것</li>
 *   <li>여러 개면 가까운 날짜 우선</li>
 *   <li>같은 날짜면 실제 예보 우선 (가상 예보는 실제 예보가 없을 때만)</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class CurrentRainForecastFinder {

    private final RainForecastRepository rainForecastRepository;

    /**
     * 이번 비 예보를 찾습니다.
     *
     * @param today 오늘
     * @return 이번 비 예보 (없으면 비어 있음)
     */
    public Optional<RainForecast> find(LocalDate today) {
        return Stream.concat(
                        rainForecastRepository.findAllActiveByRainDate(today).stream(),
                        rainForecastRepository.findAllActiveByRainDate(today.plusDays(1)).stream())
                .min(Comparator.comparing(RainForecast::getRainDate)
                        .thenComparing(RainForecast::isSimulated));
    }
}
