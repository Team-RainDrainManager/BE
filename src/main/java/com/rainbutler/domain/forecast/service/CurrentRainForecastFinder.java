package com.rainbutler.domain.forecast.service;

import com.rainbutler.domain.forecast.entity.RainForecast;
import com.rainbutler.domain.forecast.repository.RainForecastRepository;
import com.rainbutler.global.geo.KmaGridConverter.Grid;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 한 격자의 "이번 비" 예보를 고릅니다. (지도 비 예보 배너)
 *
 * <ul>
 *   <li>그 격자의 진행 중(ACTIVE) 예보 중 비 오는 날이 오늘 또는 내일인 것</li>
 *   <li>여러 개면 가까운 날짜 우선</li>
 *   <li>같은 날짜면 실제 예보 우선 (가상 예보는 실제 예보가 없을 때만)</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class CurrentRainForecastFinder {

    private final RainForecastRepository rainForecastRepository;

    /**
     * 격자의 이번 비 예보를 찾습니다.
     *
     * @param grid 기상청 격자
     * @param today 오늘
     * @return 이번 비 예보 (없으면 비어 있음)
     */
    public Optional<RainForecast> find(Grid grid, LocalDate today) {
        return Stream.concat(
                        rainForecastRepository.findAllActiveByRainDateAndGrid(
                                today, grid.nx(), grid.ny()).stream(),
                        rainForecastRepository.findAllActiveByRainDateAndGrid(
                                today.plusDays(1), grid.nx(), grid.ny()).stream())
                .min(Comparator.comparing(RainForecast::getRainDate)
                        .thenComparing(RainForecast::isSimulated));
    }
}
