package com.rainbutler.domain.weather.service;

import com.rainbutler.domain.forecast.client.KmaProperties;
import com.rainbutler.domain.weather.client.KmaObservationClient;
import com.rainbutler.domain.weather.client.Observation;
import com.rainbutler.domain.weather.client.WeatherOnDemandProperties;
import com.rainbutler.domain.weather.repository.CurrentWeatherRepository;
import com.rainbutler.global.geo.KmaGridConverter.Grid;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 지도 배너를 조회할 때, 현재 날씨 기록이 없거나 오래된 격자의 초단기실황을 기상청에서 바로 받아 저장합니다.
 *
 * <p>수집 배치는 빗물받이가 있는 격자만 받으므로, 빗물받이가 없는 지역(예: 송도)은 이 경로로 채웁니다.
 * <ul>
 *   <li>기상청 호출은 트랜잭션 밖 별도 스레드에서 하고, 저장(upsert)만 짧게 한다</li>
 *   <li>응답은 최대 {@code timeout}(기본 2초)만 기다린다. 넘으면 저장된 값으로 판단하고, 늦게 끝난 호출은 저장만 한다</li>
 *   <li><b>격자별 1건만</b>: 같은 격자를 동시에 조회하면 진행 중인 호출 결과를 함께 기다린다</li>
 *   <li><b>서버 전체 분당 상한</b>({@code max-per-minute}, 기본 30): 넘으면 부르지 않는다 (경고 로그 분당 1회)</li>
 *   <li><b>실패 격자 쉬기</b>: 실패 · 시간 초과한 격자는 {@code failure-backoff}(기본 5분) 동안 부르지 않는다
 *       (기억하는 격자 최대 1,000개, 오래된 것부터 지움)</li>
 *   <li>기상청 격자 범위(nx 1~149, ny 1~253) 밖이거나 인증키가 없으면(로컬 · CI) 부르지 않는다</li>
 * </ul>
 * 제한 · 진행 중 호출은 메모리에만 있어 컨테이너마다 따로다. 배포 중 잠깐 컨테이너가 2개면 상한도 잠깐 2배가 되지만
 * 허용한다.
 *
 * <p>지표: {@code weather_on_demand_fetch_total{result="ok|fail|timeout|limited"}}
 */
@Slf4j
@Component
public class OnDemandWeatherFetcher {

    /** 동시에 기상청을 부르는 스레드 수 */
    private static final int THREADS = 4;
    /** 실패 시각을 기억하는 최대 격자 수 */
    private static final int MAX_FAILED_GRIDS = 1000;
    private static final int MAX_NX = 149;
    private static final int MAX_NY = 253;

    private final KmaObservationClient kmaObservationClient;
    private final CurrentWeatherRepository currentWeatherRepository;
    private final KmaProperties kmaProperties;
    private final WeatherOnDemandProperties properties;
    private final MeterRegistry meterRegistry;
    private final ExecutorService executor = Executors.newFixedThreadPool(THREADS, runnable -> {
        Thread thread = new Thread(runnable, "weather-on-demand");
        thread.setDaemon(true);
        return thread;
    });

    /** 격자별 진행 중인 호출 (같은 격자는 1건만) */
    private final Map<Grid, CompletableFuture<Optional<Observation>>> inFlight =
            new ConcurrentHashMap<>();
    /** 격자별 마지막 실패 시각 (넣은 순서대로, 상한을 넘으면 가장 오래된 것부터 지움) */
    private final Map<Grid, LocalDateTime> failedAt = Collections.synchronizedMap(
            new LinkedHashMap<>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Grid, LocalDateTime> eldest) {
                    return size() > MAX_FAILED_GRIDS;
                }
            });

    /** 분당 상한 계산용 (지금 분 · 그 분에 쓴 호출 수 · 경고를 남긴 분) */
    private LocalDateTime rateWindow;
    private int rateUsed;
    private LocalDateTime warnedWindow;

    public OnDemandWeatherFetcher(
            @Qualifier("kmaOnDemandObservationClient") KmaObservationClient kmaObservationClient,
            CurrentWeatherRepository currentWeatherRepository,
            KmaProperties kmaProperties,
            WeatherOnDemandProperties properties,
            MeterRegistry meterRegistry) {
        this.kmaObservationClient = kmaObservationClient;
        this.currentWeatherRepository = currentWeatherRepository;
        this.kmaProperties = kmaProperties;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    /**
     * 격자의 초단기실황을 받아 저장합니다. 실패 · 시간 초과 · 제한이면 아무것도 바꾸지 않습니다. (예외를 던지지 않음)
     *
     * @param grid 기상청 격자
     * @param now 현재 시각
     */
    public void refresh(Grid grid, LocalDateTime now) {
        if (!kmaProperties.isConfigured() || !inKmaRange(grid)) {
            return;
        }
        if (inBackoff(grid, now)) {
            count("limited");
            return;
        }
        CompletableFuture<Optional<Observation>> call = inFlight.get(grid);
        if (call == null) {
            if (!tryAcquire(now)) {
                count("limited");
                return;
            }
            CompletableFuture<Optional<Observation>> created = new CompletableFuture<>();
            call = inFlight.putIfAbsent(grid, created);
            if (call == null) {
                call = created;
                start(grid, now, created);
            }
        }
        await(grid, now, call);
    }

    private void start(Grid grid, LocalDateTime now,
            CompletableFuture<Optional<Observation>> call) {
        try {
            executor.execute(() -> {
                try {
                    call.complete(fetchAndSave(grid, now));
                    failedAt.remove(grid);
                    count("ok");
                } catch (RuntimeException e) {
                    failedAt.put(grid, now);
                    count("fail");
                    call.completeExceptionally(e);
                } finally {
                    inFlight.remove(grid, call);
                }
            });
        } catch (RejectedExecutionException e) {
            // 종료 중이면 부르지 않는다
            inFlight.remove(grid, call);
            call.complete(Optional.empty());
        }
    }

    private void await(Grid grid, LocalDateTime now,
            CompletableFuture<Optional<Observation>> call) {
        try {
            call.get(properties.timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            failedAt.put(grid, now);
            count("timeout");
            log.warn("[현재 날씨] 격자 {},{} 조회 시 기상청 호출 시간 초과 ({}ms), 저장된 값으로 판단합니다.",
                    grid.nx(), grid.ny(), properties.timeout().toMillis());
        } catch (ExecutionException e) {
            log.warn("[현재 날씨] 격자 {},{} 조회 시 기상청 호출 실패, 저장된 값으로 판단합니다: {}",
                    grid.nx(), grid.ny(), e.getCause() == null ? e.getMessage()
                            : e.getCause().getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Optional<Observation> fetchAndSave(Grid grid, LocalDateTime now) {
        long startedAt = System.currentTimeMillis();
        Optional<Observation> observation = kmaObservationClient.fetch(grid, now);
        observation.ifPresent(o -> {
            currentWeatherRepository.saveObservation(
                    grid, o.pty(), o.rn1Mm(), o.raining(), o.observedAt(), now);
            log.info("[현재 날씨] 격자 {},{} 조회 시 기상청 호출 ({}ms, 결과 raining={})", grid.nx(),
                    grid.ny(), System.currentTimeMillis() - startedAt, o.raining());
        });
        return observation;
    }

    private boolean inBackoff(Grid grid, LocalDateTime now) {
        LocalDateTime failed = failedAt.get(grid);
        return failed != null && now.isBefore(failed.plus(properties.failureBackoff()));
    }

    /**
     * 분당 상한 안이면 1건을 씁니다. 넘으면 그 분에 한 번만 경고합니다.
     */
    private synchronized boolean tryAcquire(LocalDateTime now) {
        LocalDateTime window = now.truncatedTo(ChronoUnit.MINUTES);
        if (!window.equals(rateWindow)) {
            rateWindow = window;
            rateUsed = 0;
        }
        if (rateUsed < properties.maxPerMinute()) {
            rateUsed++;
            return true;
        }
        if (!window.equals(warnedWindow)) {
            warnedWindow = window;
            log.warn("[현재 날씨] 조회 시 기상청 호출이 분당 상한 {}건을 넘어 저장된 값으로 판단합니다.",
                    properties.maxPerMinute());
        }
        return false;
    }

    private static boolean inKmaRange(Grid grid) {
        return grid.nx() >= 1 && grid.nx() <= MAX_NX && grid.ny() >= 1 && grid.ny() <= MAX_NY;
    }

    private void count(String result) {
        meterRegistry.counter("weather.on.demand.fetch", "result", result).increment();
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
