package com.rainbutler.domain.weather.service;

import com.rainbutler.domain.forecast.client.KmaProperties;
import com.rainbutler.domain.weather.client.KmaObservationClient;
import com.rainbutler.domain.weather.client.Observation;
import com.rainbutler.domain.weather.client.WeatherOnDemandProperties;
import com.rainbutler.domain.weather.repository.CurrentWeatherRepository;
import com.rainbutler.global.geo.KmaGridConverter.Grid;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
 *   <li>인증키가 없으면(로컬 · CI) 부르지 않는다</li>
 * </ul>
 */
@Slf4j
@Component
public class OnDemandWeatherFetcher {

    /** 동시에 기상청을 부르는 스레드 수 */
    private static final int THREADS = 4;

    private final KmaObservationClient kmaObservationClient;
    private final CurrentWeatherRepository currentWeatherRepository;
    private final KmaProperties kmaProperties;
    private final WeatherOnDemandProperties properties;
    private final ExecutorService executor = Executors.newFixedThreadPool(THREADS, runnable -> {
        Thread thread = new Thread(runnable, "weather-on-demand");
        thread.setDaemon(true);
        return thread;
    });

    public OnDemandWeatherFetcher(
            @Qualifier("kmaOnDemandObservationClient") KmaObservationClient kmaObservationClient,
            CurrentWeatherRepository currentWeatherRepository,
            KmaProperties kmaProperties,
            WeatherOnDemandProperties properties) {
        this.kmaObservationClient = kmaObservationClient;
        this.currentWeatherRepository = currentWeatherRepository;
        this.kmaProperties = kmaProperties;
        this.properties = properties;
    }

    /**
     * 격자의 초단기실황을 받아 저장합니다. 실패 · 시간 초과면 아무것도 바꾸지 않습니다. (예외를 던지지 않음)
     *
     * @param grid 기상청 격자
     * @param now 현재 시각
     */
    public void refresh(Grid grid, LocalDateTime now) {
        if (!kmaProperties.isConfigured()) {
            return;
        }
        CompletableFuture<Optional<Observation>> call =
                CompletableFuture.supplyAsync(() -> fetchAndSave(grid, now), executor);
        try {
            call.get(properties.timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
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

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
