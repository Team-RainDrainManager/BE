package com.rainbutler.domain.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.rainbutler.domain.weather.client.WeatherOnDemandProperties;
import com.rainbutler.domain.weather.repository.CurrentWeatherRepository;
import com.rainbutler.domain.weather.repository.CurrentWeatherRepository.CurrentWeather;
import com.rainbutler.global.geo.KmaGridConverter.Grid;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 배너 현재 비 판단 전에 기상청에서 다시 받아 올지 정하는 규칙을 검증합니다.
 */
class CurrentWeatherServiceTest {

    /** 10:50 → 기상청이 지금 낼 수 있는 가장 최근 실황은 10:00 */
    private static final LocalDateTime NOW = LocalDateTime.of(2028, 6, 1, 10, 50);
    private static final LocalDateTime LATEST = NOW.withMinute(0);
    private static final Grid GRID = new Grid(54, 123);

    private final CurrentWeatherRepository repository = mock(CurrentWeatherRepository.class);
    private final OnDemandWeatherFetcher fetcher = mock(OnDemandWeatherFetcher.class);
    private final CurrentWeatherService service = new CurrentWeatherService(repository, fetcher,
            new WeatherOnDemandProperties(Duration.ofSeconds(2), Duration.ofMinutes(10), 30,
                    Duration.ofMinutes(5)));

    @Test
    void 기록이_없으면_받아_오고_저장된_값으로_판단한다() {
        given(repository.find(GRID)).willReturn(Optional.empty(),
                Optional.of(weather(true, LATEST, NOW, null)));

        assertThat(service.status(GRID, NOW).raining()).isTrue();
        verify(fetcher).refresh(GRID, NOW);
    }

    @Test
    void 최근_실황이면_받아_오지_않는다() {
        given(repository.find(GRID)).willReturn(Optional.of(weather(true, LATEST, NOW, null)));

        assertThat(service.status(GRID, NOW).raining()).isTrue();
        verify(fetcher, never()).refresh(any(), any());
    }

    @Test
    void 오래된_실황은_받아_오지만_마지막_호출_10분_안이면_다시_부르지_않는다() {
        given(repository.find(GRID)).willReturn(
                Optional.of(weather(false, LATEST.minusHours(1), NOW.minusMinutes(5), null)));
        service.status(GRID, NOW);
        verify(fetcher, never()).refresh(any(), any());

        given(repository.find(GRID)).willReturn(
                Optional.of(weather(false, LATEST.minusHours(1), NOW.minusMinutes(11), null)));
        service.status(GRID, NOW);
        verify(fetcher, times(1)).refresh(GRID, NOW);
    }

    @Test
    void 받아_오지_못하면_기록이_없을_때_비_아님이다() {
        given(repository.find(GRID)).willReturn(Optional.empty());

        assertThat(service.status(GRID, NOW).raining()).isFalse();
        verify(fetcher).refresh(GRID, NOW);
    }

    @Test
    void 시연용_강제_비_중에는_부르지_않고_비_오는_중이다() {
        given(repository.find(GRID)).willReturn(
                Optional.of(weather(false, null, NOW.minusHours(5), NOW.plusMinutes(30))));

        var status = service.status(GRID, NOW);

        assertThat(status.raining()).isTrue();
        assertThat(status.simulated()).isTrue();
        verify(fetcher, never()).refresh(any(), any());
    }

    private static CurrentWeather weather(boolean raining, LocalDateTime observedAt,
            LocalDateTime fetchedAt, LocalDateTime simulatedUntil) {
        return new CurrentWeather(raining ? 1 : 0, BigDecimal.ZERO, raining, observedAt, fetchedAt,
                simulatedUntil);
    }
}
