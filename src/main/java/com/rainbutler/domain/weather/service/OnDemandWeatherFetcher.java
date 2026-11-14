package com.rainbutler.domain.weather.service;

import com.rainbutler.domain.forecast.client.KmaProperties;
import com.rainbutler.domain.weather.client.KmaObservationClient;
import com.rainbutler.domain.weather.client.Observation;
import com.rainbutler.domain.weather.client.WeatherOnDemandProperties;
import com.rainbutler.domain.weather.repository.CurrentWeatherRepository;
import com.rainbutler.global.geo.KmaGridConverter.Grid;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;

/**
 * 지도 배너를 조회할 때, 현재 날씨 기록이 없거나 오래된 격자의 초단기실황을 기상청에서 받아 저장합니다.
 *
 * <p>수집 배치는 빗물받이가 있는 격자만 받으므로, 빗물받이가 없는 지역(예: 송도)은 이 경로로 채웁니다.
 * <ul>
 *   <li><b>응답은 짧게, 호출은 끝까지</b>: 기상청 호출은 전용 스레드 풀에서 하고, 배너 요청은
 *       {@code response-timeout}(기본 2초)만 기다린다. 넘으면 저장된 값으로 응답하고, 호출은
 *       {@code fetch-timeout}(기본 10초)까지 계속해 성공하면 저장한다 → 다음 조회부터 최신 값.
 *       기상청 API허브가 발표 직후 3~10초 걸릴 때가 있어서다</li>
 *   <li><b>격자별 1건만</b>: 같은 격자 호출이 진행 중이면 새로 부르지 않고 그 결과를 (최대 2초) 기다린다</li>
 *   <li><b>서버 전체 분당 상한</b>({@code max-per-minute}, 기본 30): 실제 기상청 호출 수 기준. 넘거나 스레드 풀
 *       대기열(50건)이 가득 차면 부르지 않는다 (경고 로그 분당 1회)</li>
 *   <li><b>실패 격자 쉬기</b>: 10초를 넘었거나 에러가 난 격자만 {@code failure-backoff}(기본 5분) 동안 부르지 않는다.
 *       2초 응답 대기 초과는 실패가 아니다 (기억하는 격자 최대 1,000개, 오래된 것부터 지움)</li>
 *   <li>기상청 격자 범위(nx 1~149, ny 1~253) 밖이거나 인증키가 없으면(로컬 · CI) 부르지 않는다</li>
 *   <li>호출은 트랜잭션 밖, 저장(upsert)만 짧게. 백그라운드 예외는 삼키고 로그만 남긴다</li>
 * </ul>
 * 제한 · 진행 중 호출은 메모리에만 있어 컨테이너마다 따로다. 배포 중 잠깐 컨테이너가 2개면 상한도 잠깐 2배가 되지만
 * 허용한다.
 *
 * <p>지표: {@code weather_on_demand_fetch_total{result="ok|fail|timeout|limited"}}
 * (timeout = 10초 초과)
 */
@Slf4j
@Component
public class OnDemandWeatherFetcher {

    /** 실패 시각을 기억하는 최대 격자 수 */
    private static final int MAX_FAILED_GRIDS = 1000;
    private static final int MAX_NX = 149;
    private static final int MAX_NY = 253;

    private final KmaObservationClient kmaObservationClient;
    private final CurrentWeatherRepository currentWeatherRepository;
    private final KmaProperties kmaProperties;
    private final WeatherOnDemandProperties properties;
    private final MeterRegistry meterRegistry;
    private final TaskExecutor executor;

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
            MeterRegistry meterRegistry,
            @Qualifier("weatherOnDemandExecutor") TaskExecutor executor) {
        this.kmaObservationClient = kmaObservationClient;
        this.currentWeatherRepository = currentWeatherRepository;
        this.kmaProperties = kmaProperties;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.executor = executor;
    }

    /**
     * 격자의 초단기실황을 받아 저장합니다. 최대 {@code response-timeout}만 기다리고, 그 안에 끝나지 않으면 호출은 뒤에서
     * 계속합니다. 제한 · 실패면 아무것도 바꾸지 않습니다. (예외를 던지지 않음)
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
                if (!start(grid, now, created)) {
                    return;
                }
            }
        }
        await(grid, call);
    }

    /**
     * 백그라운드 호출을 시작합니다. 스레드 풀 대기열이 가득 차면 부르지 않습니다.
     *
     * @return 시작했으면 true
     */
    private boolean start(Grid grid, LocalDateTime now,
            CompletableFuture<Optional<Observation>> call) {
        long startedAt = System.nanoTime();
        try {
            executor.execute(() -> {
                try {
                    call.complete(fetchAndSave(grid, now));
                } catch (RuntimeException e) {
                    // 예외는 호출 결과로만 넘기고 스레드는 살려 둔다
                    call.completeExceptionally(e);
                }
            });
        } catch (TaskRejectedException e) {
            inFlight.remove(grid, call);
            call.complete(Optional.empty());
            count("limited");
            log.warn("[현재 날씨] 조회 시 기상청 호출 대기열이 가득 차 격자 {},{}는 저장된 값으로 판단합니다.",
                    grid.nx(), grid.ny());
            return false;
        }
        // 제출된 호출만 10초 상한 · 끝난 뒤 기록을 건다 (이미 끝났으면 바로 실행된다)
        call.orTimeout(properties.fetchTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .whenComplete((observation, error) -> finish(grid, now, call, startedAt,
                        observation, error));
        return true;
    }

    /**
     * 호출이 끝나면(성공 · 실패 · 10초 초과) 진행 중 목록에서 빼고 기록 · 로그를 남깁니다.
     */
    private void finish(Grid grid, LocalDateTime now, CompletableFuture<Optional<Observation>> call,
            long startedAt, Optional<Observation> observation, Throwable error) {
        inFlight.remove(grid, call);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        if (error == null) {
            failedAt.remove(grid);
            count("ok");
            long responseMs = properties.responseTimeout().toMillis();
            log.info("[현재 날씨] 격자 {},{} 조회 시 기상청 호출 완료 ({}ms, 응답 대기 {}ms, raining={}, "
                    + "background={})", grid.nx(), grid.ny(), elapsedMs,
                    Math.min(elapsedMs, responseMs),
                    observation != null && observation.map(Observation::raining).orElse(false),
                    elapsedMs > responseMs);
            return;
        }
        failedAt.put(grid, now);
        if (error instanceof TimeoutException) {
            count("timeout");
            log.warn("[현재 날씨] 격자 {},{} 조회 시 기상청 호출이 {}ms를 넘어 실패로 보고 {}분 쉽니다.",
                    grid.nx(), grid.ny(), properties.fetchTimeout().toMillis(),
                    properties.failureBackoff().toMinutes());
        } else {
            count("fail");
            log.warn("[현재 날씨] 격자 {},{} 조회 시 기상청 호출 실패 ({}ms), {}분 쉽니다: {}", grid.nx(),
                    grid.ny(), elapsedMs, properties.failureBackoff().toMinutes(),
                    error.getMessage());
        }
    }

    /**
     * 결과를 최대 {@code response-timeout}만 기다립니다. 넘어도 실패가 아니며 호출은 뒤에서 계속됩니다.
     */
    private void await(Grid grid, CompletableFuture<Optional<Observation>> call) {
        try {
            call.get(properties.responseTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.debug("[현재 날씨] 격자 {},{} 응답 대기 {}ms 초과, 저장된 값으로 응답하고 호출은 뒤에서 계속합니다.",
                    grid.nx(), grid.ny(), properties.responseTimeout().toMillis());
        } catch (ExecutionException e) {
            // 실패 기록 · 로그는 finish에서 남긴다
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Optional<Observation> fetchAndSave(Grid grid, LocalDateTime now) {
        Optional<Observation> observation = kmaObservationClient.fetch(grid, now);
        observation.ifPresent(o -> currentWeatherRepository.saveObservation(
                grid, o.pty(), o.rn1Mm(), o.raining(), o.observedAt(), now));
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
}
