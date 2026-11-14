package com.rainbutler.domain.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.rainbutler.domain.forecast.client.KmaApiException;
import com.rainbutler.domain.forecast.client.KmaProperties;
import com.rainbutler.domain.weather.client.KmaObservationClient;
import com.rainbutler.domain.weather.client.Observation;
import com.rainbutler.domain.weather.client.WeatherOnDemandProperties;
import com.rainbutler.domain.weather.repository.CurrentWeatherRepository;
import com.rainbutler.global.geo.KmaGridConverter.Grid;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 배너 조회 시 기상청 호출의 동시 1건 · 분당 상한 · 실패 쉬기 · 시간 초과 · 범위 · 키 없음을 검증합니다.
 */
class OnDemandWeatherFetcherTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2028, 6, 1, 10, 50);
    private static final Grid SONGDO = new Grid(54, 123);
    private static final Observation RAIN =
            new Observation(1, new BigDecimal("2.0"), NOW.withMinute(0));

    private final KmaObservationClient client = mock(KmaObservationClient.class);
    private final CurrentWeatherRepository repository = mock(CurrentWeatherRepository.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private OnDemandWeatherFetcher fetcher;

    @AfterEach
    void tearDown() {
        if (fetcher != null) {
            fetcher.shutdown();
        }
    }

    @Test
    void 받아_온_실황을_저장한다() {
        fetcher = fetcher("key", Duration.ofSeconds(2), 30);
        given(client.fetch(SONGDO, NOW)).willReturn(Optional.of(RAIN));

        fetcher.refresh(SONGDO, NOW);

        verify(repository).saveObservation(SONGDO, 1, new BigDecimal("2.0"), true,
                NOW.withMinute(0), NOW);
        assertThat(count("ok")).isEqualTo(1);
    }

    @Test
    void 같은_격자_동시_10건은_기상청을_한_번만_부른다() throws Exception {
        fetcher = fetcher("key", Duration.ofSeconds(2), 30);
        CountDownLatch release = new CountDownLatch(1);
        given(client.fetch(SONGDO, NOW)).willAnswer(invocation -> {
            release.await(2, TimeUnit.SECONDS);
            return Optional.of(RAIN);
        });

        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Future<?>> calls = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            calls.add(pool.submit(() -> fetcher.refresh(SONGDO, NOW)));
        }
        Thread.sleep(200);
        release.countDown();
        for (Future<?> call : calls) {
            call.get(3, TimeUnit.SECONDS);
        }
        pool.shutdown();

        verify(client, times(1)).fetch(SONGDO, NOW);
    }

    @Test
    void 분당_상한을_넘으면_부르지_않고_다음_분에는_다시_부른다() {
        fetcher = fetcher("key", Duration.ofSeconds(2), 3);
        given(client.fetch(any(), any())).willReturn(Optional.of(RAIN));

        for (int nx = 1; nx <= 4; nx++) {
            fetcher.refresh(new Grid(nx, 100), NOW);
        }

        verify(client, times(3)).fetch(any(), any());
        assertThat(count("limited")).isEqualTo(1);

        fetcher.refresh(new Grid(5, 100), NOW.plusMinutes(1));
        verify(client, times(4)).fetch(any(), any());
    }

    @Test
    void 실패한_격자는_5분_동안_다시_부르지_않는다() {
        fetcher = fetcher("key", Duration.ofSeconds(2), 30);
        given(client.fetch(any(), any())).willThrow(new KmaApiException("기상청 오류"));

        fetcher.refresh(SONGDO, NOW);
        fetcher.refresh(SONGDO, NOW.plusMinutes(4));
        verify(client, times(1)).fetch(any(), any());

        fetcher.refresh(SONGDO, NOW.plusMinutes(5));
        verify(client, times(2)).fetch(any(), any());
        assertThat(count("fail")).isEqualTo(2);
        verify(repository, never()).saveObservation(any(), any(), any(), any(Boolean.class),
                any(), any());
    }

    @Test
    void 시간_초과면_기다리지_않고_돌아오고_그_격자는_쉰다() {
        fetcher = fetcher("key", Duration.ofMillis(200), 30);
        given(client.fetch(any(), any())).willAnswer(invocation -> {
            Thread.sleep(1_000);
            return Optional.of(RAIN);
        });

        long startedAt = System.currentTimeMillis();
        fetcher.refresh(SONGDO, NOW);
        assertThat(System.currentTimeMillis() - startedAt).isLessThan(900);
        assertThat(count("timeout")).isEqualTo(1);

        fetcher.refresh(SONGDO, NOW.plusMinutes(1));
        verify(client, times(1)).fetch(any(), any());
    }

    @Test
    void 격자_범위_밖이나_키가_없으면_부르지_않는다() {
        fetcher = fetcher("key", Duration.ofSeconds(2), 30);
        fetcher.refresh(new Grid(0, 100), NOW);
        fetcher.refresh(new Grid(150, 100), NOW);
        fetcher.refresh(new Grid(50, 254), NOW);
        fetcher.shutdown();

        fetcher = fetcher("", Duration.ofSeconds(2), 30);
        fetcher.refresh(SONGDO, NOW);

        verify(client, never()).fetch(any(), any());
    }

    private OnDemandWeatherFetcher fetcher(String key, Duration timeout, int maxPerMinute) {
        KmaProperties kma = new KmaProperties(key, "https://kma.test/fcst", "https://kma.test/ncst",
                57, 125, 200);
        WeatherOnDemandProperties properties = new WeatherOnDemandProperties(
                timeout, Duration.ofMinutes(10), maxPerMinute, Duration.ofMinutes(5));
        return new OnDemandWeatherFetcher(client, repository, kma, properties, meterRegistry);
    }

    private double count(String result) {
        return meterRegistry.counter("weather.on.demand.fetch", "result", result).count();
    }
}
