package com.rainbutler.domain.weather.job;

import com.rainbutler.domain.forecast.client.KmaApiException;
import com.rainbutler.domain.forecast.service.WeatherGridTargets;
import com.rainbutler.domain.weather.client.KmaObservationClient;
import com.rainbutler.domain.weather.client.Observation;
import com.rainbutler.domain.weather.repository.CurrentWeatherRepository;
import com.rainbutler.global.geo.KmaGridConverter.Grid;
import com.rainbutler.global.scheduling.SchedulingProperties;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 기상청 초단기실황 수집 배치입니다. (매시 45분 — 정시 발표 40분 뒤부터 조회 가능)
 *
 * <p>빗물받이가 있는 격자({@link WeatherGridTargets})마다 강수형태 · 1시간 강수량을 {@code current_weather}의 그
 * 격자 행에 저장합니다. 한 격자 호출이 실패하면 그 격자만 이번 회차는 저장하지 않고(나머지는 계속), 값이 3시간보다
 * 오래되면 배너에서 "비 아님"으로 처리됩니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CurrentWeatherJob {

    private final KmaObservationClient kmaObservationClient;
    private final CurrentWeatherRepository currentWeatherRepository;
    private final WeatherGridTargets weatherGridTargets;
    private final SchedulingProperties schedulingProperties;
    private final Clock clock;

    /**
     * 정해진 시각에 수집합니다. 스케줄러가 꺼져 있으면(테스트) 아무것도 하지 않습니다.
     */
    @Scheduled(cron = "0 45 * * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "currentWeatherCollect")
    public void scheduledCollect() {
        if (!schedulingProperties.enabled()) {
            return;
        }
        collect();
    }

    /**
     * 모든 대상 격자의 초단기실황을 수집해 저장합니다.
     */
    public void collect() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Grid> grids = weatherGridTargets.find();
        long startedAt = System.currentTimeMillis();
        int failed = 0;
        for (Grid grid : grids) {
            if (!collect(grid, now)) {
                failed++;
            }
        }
        log.info("[현재 날씨 수집] 격자 {}개 수집 (실패 {}개), {}ms", grids.size(), failed,
                System.currentTimeMillis() - startedAt);
    }

    private boolean collect(Grid grid, LocalDateTime now) {
        Optional<Observation> observation;
        try {
            observation = kmaObservationClient.fetch(grid, now);
        } catch (KmaApiException e) {
            log.error("[현재 날씨 수집] 격자 {},{} 기상청 호출 실패로 이번 회차를 건너뜁니다: {}", grid.nx(),
                    grid.ny(), e.getMessage());
            return false;
        } catch (RuntimeException e) {
            log.error("[현재 날씨 수집] 격자 {},{} 수집 실패: {}", grid.nx(), grid.ny(),
                    e.getMessage(), e);
            return false;
        }
        observation.ifPresent(o -> {
            currentWeatherRepository.saveObservation(
                    grid, o.pty(), o.rn1Mm(), o.raining(), o.observedAt(), now);
            log.info("[현재 날씨 수집] 격자 {},{} 기준 {} 강수형태 {} · 1시간 강수량 {}mm · 비 {}",
                    grid.nx(), grid.ny(), o.observedAt(), o.pty(), o.rn1Mm(), o.raining());
        });
        return true;
    }
}
