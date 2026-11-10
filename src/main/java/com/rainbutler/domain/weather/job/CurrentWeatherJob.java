package com.rainbutler.domain.weather.job;

import com.rainbutler.domain.forecast.client.KmaApiException;
import com.rainbutler.domain.weather.client.KmaObservationClient;
import com.rainbutler.domain.weather.client.Observation;
import com.rainbutler.domain.weather.repository.CurrentWeatherRepository;
import com.rainbutler.global.scheduling.SchedulingProperties;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 기상청 초단기실황 수집 배치입니다. (매시 45분 — 정시 발표 40분 뒤부터 조회 가능)
 *
 * <p>파일럿 격자의 강수형태 · 1시간 강수량을 {@code current_weather} 1행에 저장합니다. 호출이 실패하면 이번 회차는
 * 저장하지 않고, 값이 3시간보다 오래되면 배너에서 "비 아님"으로 처리됩니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CurrentWeatherJob {

    private final KmaObservationClient kmaObservationClient;
    private final CurrentWeatherRepository currentWeatherRepository;
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
     * 초단기실황을 수집해 저장합니다.
     */
    public void collect() {
        LocalDateTime now = LocalDateTime.now(clock);
        Optional<Observation> observation;
        try {
            observation = kmaObservationClient.fetch(now);
        } catch (KmaApiException e) {
            log.error("[현재 날씨 수집] 기상청 호출 실패로 이번 회차를 건너뜁니다: {}", e.getMessage());
            return;
        }
        observation.ifPresent(o -> {
            currentWeatherRepository.saveObservation(
                    o.pty(), o.rn1Mm(), o.raining(), o.observedAt(), now);
            log.info("[현재 날씨 수집] 기준 {} 강수형태 {} · 1시간 강수량 {}mm · 비 {}",
                    o.observedAt(), o.pty(), o.rn1Mm(), o.raining());
        });
    }
}
