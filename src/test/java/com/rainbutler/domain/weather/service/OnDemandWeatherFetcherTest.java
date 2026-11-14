package com.rainbutler.domain.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
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
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 배너 조회 시 기상청 호출을 검증합니다. 응답은 짧게 끊고 호출은 뒤에서 끝까지 받아 저장하며, 동시 1건 · 분당 상한 ·
 * 대기열 상한 · 실패 쉬기 · 범위 · 키 없음 규칙을 지키는지 봅니다. (시간은 비율을 유지해 줄여서 흉내 낸다)
 */
class OnDemandWeatherFetcherTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2028, 6, 1, 10, 50);
    private static final Grid SONGDO = new Grid(54, 123);
    private static final Observation RAIN =
            new Observation(1, new BigDecimal("2.0"), NOW.withMinute(0));
    /** 응답 대기 2초 · 호출 상한 10초를 줄인 값 */
    private static final Duration RESPONSE = Duration.ofMillis(200);
    private static final Duration FETCH = Duration.ofMillis(1_000);

    private final KmaObservationClient client = mock(KmaObservationClient.class);
    private final CurrentWeatherRepository repository = mock(CurrentWeatherRepository.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private ThreadPoolTaskExecutor executor;
    private OnDemandWeatherFetcher fetcher;

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    @Test
    void 바로_오면_저장하고_응답한다() {
        fetcher = fetcher("key", 30, 3, 50);
        given(client.fetch(SONGDO, NOW)).willReturn(Optional.of(RAIN));

        fetcher.refresh(SONGDO, NOW);

        verify(repository).saveObservation(SONGDO, 1, new BigDecimal("2.0"), true,
                NOW.withMinute(0), NOW);
        await().atMost(Duration.ofSeconds(1)).until(() -> count("ok") == 1);
    }

    @Test
    void 느리면_응답은_먼저_하고_뒤에서_끝까지_받아_저장한다_실패가_아니다() {
        fetcher = fetcher("key", 30, 3, 50);
        // 기상청 3초 응답 흉내 (응답 대기 2초보다 길고 호출 상한 10초보다 짧다)
        given(client.fetch(any(), any())).willAnswer(invocation -> {
            Thread.sleep(RESPONSE.toMillis() * 3 / 2);
            return Optional.of(RAIN);
        });

        long startedAt = System.currentTimeMillis();
        fetcher.refresh(SONGDO, NOW);
        assertThat(System.currentTimeMillis() - startedAt).isLessThan(RESPONSE.toMillis() + 150);
        verify(repository, never()).saveObservation(any(), any(), any(), any(Boolean.class),
                any(), any());

        // 뒤에서 끝나면 저장된다 → 다음 조회부터 최신 값
        await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> verify(repository)
                .saveObservation(SONGDO, 1, new BigDecimal("2.0"), true, NOW.withMinute(0),
                        NOW));
        await().atMost(Duration.ofSeconds(1)).until(() -> count("ok") == 1);
        assertThat(count("timeout") + count("fail")).isZero();

        // 실패가 아니므로 쉬지 않는다
        fetcher.refresh(SONGDO, NOW.plusMinutes(1));
        verify(client, times(2)).fetch(any(), any());
    }

    @Test
    void 호출_상한을_넘으면_실패로_보고_5분_동안_다시_부르지_않는다() {
        fetcher = fetcher("key", 30, 3, 50);
        // 기상청 11초 흉내 (호출 상한 10초보다 길다)
        given(client.fetch(any(), any())).willAnswer(invocation -> {
            Thread.sleep(FETCH.toMillis() * 11 / 10);
            return Optional.of(RAIN);
        });

        fetcher.refresh(SONGDO, NOW);
        await().atMost(Duration.ofSeconds(2)).until(() -> count("timeout") == 1);

        fetcher.refresh(SONGDO, NOW.plusMinutes(4));
        verify(client, times(1)).fetch(any(), any());
        fetcher.refresh(SONGDO, NOW.plusMinutes(5));
        verify(client, times(2)).fetch(any(), any());
    }

    @Test
    void 에러가_나면_실패로_보고_5분_동안_다시_부르지_않는다() {
        fetcher = fetcher("key", 30, 3, 50);
        given(client.fetch(any(), any())).willThrow(new KmaApiException("기상청 오류"));

        fetcher.refresh(SONGDO, NOW);
        await().atMost(Duration.ofSeconds(1)).until(() -> count("fail") == 1);
        fetcher.refresh(SONGDO, NOW.plusMinutes(4));

        verify(client, times(1)).fetch(any(), any());
    }

    @Test
    void 같은_격자_호출이_진행_중이면_새로_부르지_않는다() throws Exception {
        fetcher = fetcher("key", 30, 3, 50);
        CountDownLatch release = new CountDownLatch(1);
        given(client.fetch(SONGDO, NOW)).willAnswer(invocation -> {
            release.await(2, TimeUnit.SECONDS);
            return Optional.of(RAIN);
        });

        // 동시 10건
        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Future<?>> calls = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            calls.add(pool.submit(() -> fetcher.refresh(SONGDO, NOW)));
        }
        for (Future<?> call : calls) {
            call.get(2, TimeUnit.SECONDS);
        }
        pool.shutdown();
        // 응답 대기가 끝난 뒤(백그라운드 진행 중)에 다시 조회해도 새로 부르지 않는다
        fetcher.refresh(SONGDO, NOW);
        release.countDown();

        verify(client, times(1)).fetch(SONGDO, NOW);
    }

    @Test
    void 분당_상한을_넘으면_부르지_않고_다음_분에는_다시_부른다() {
        fetcher = fetcher("key", 3, 3, 50);
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
    void 대기열이_가득_차면_부르지_않고_바로_돌아온다() {
        // 스레드 1개 · 대기 1건: 첫 호출이 스레드를 잡고, 두 번째가 대기, 세 번째는 거절
        fetcher = fetcher("key", 30, 1, 1);
        CountDownLatch release = new CountDownLatch(1);
        given(client.fetch(any(), any())).willAnswer(invocation -> {
            release.await(2, TimeUnit.SECONDS);
            return Optional.of(RAIN);
        });

        fetcher.refresh(new Grid(1, 100), NOW);
        fetcher.refresh(new Grid(2, 100), NOW);
        long startedAt = System.currentTimeMillis();
        fetcher.refresh(new Grid(3, 100), NOW);
        assertThat(System.currentTimeMillis() - startedAt).isLessThan(RESPONSE.toMillis());
        release.countDown();

        await().atMost(Duration.ofSeconds(2)).untilAsserted(
                () -> verify(client, times(2)).fetch(any(), any()));
        assertThat(count("limited")).isEqualTo(1);
    }

    @Test
    void 격자_범위_밖이나_키가_없으면_부르지_않는다() {
        fetcher = fetcher("key", 30, 3, 50);
        fetcher.refresh(new Grid(0, 100), NOW);
        fetcher.refresh(new Grid(150, 100), NOW);
        fetcher.refresh(new Grid(50, 254), NOW);
        executor.shutdown();

        fetcher = fetcher("", 30, 3, 50);
        fetcher.refresh(SONGDO, NOW);

        verify(client, never()).fetch(any(), any());
    }

    private OnDemandWeatherFetcher fetcher(String key, int maxPerMinute, int threads, int queue) {
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setQueueCapacity(queue);
        executor.initialize();
        KmaProperties kma = new KmaProperties(key, "https://kma.test/fcst", "https://kma.test/ncst",
                57, 125, 200);
        WeatherOnDemandProperties properties = new WeatherOnDemandProperties(
                RESPONSE, FETCH, Duration.ofMinutes(10), maxPerMinute, Duration.ofMinutes(5));
        return new OnDemandWeatherFetcher(client, repository, kma, properties, meterRegistry,
                executor);
    }

    private double count(String result) {
        return meterRegistry.counter("weather.on.demand.fetch", "result", result).count();
    }
}
