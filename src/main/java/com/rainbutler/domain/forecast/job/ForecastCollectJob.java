package com.rainbutler.domain.forecast.job;

import com.rainbutler.domain.forecast.client.DailyRain;
import com.rainbutler.domain.forecast.client.KmaApiException;
import com.rainbutler.domain.forecast.client.KmaForecastClient;
import com.rainbutler.domain.forecast.service.ForecastService;
import com.rainbutler.global.config.PolicyProperties;
import com.rainbutler.global.scheduling.SchedulingProperties;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 기상청 예보 수집 배치입니다. (단기예보 발표 15분 뒤, 하루 8번)
 *
 * <p>오늘 · 내일 예보를 받아 날짜마다 {@link ForecastService#apply}로 확정 · 갱신 · 취소합니다. 기상청 호출이
 * 실패하면 이번 회차는 예보를 바꾸지 않고 다음 회차를 기다립니다. 그날 데이터가 응답에 없을 때도 바꾸지 않습니다
 * (데이터가 없다고 취소하지 않는다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ForecastCollectJob {

    private final KmaForecastClient kmaForecastClient;
    private final ForecastService forecastService;
    private final PolicyProperties policy;
    private final SchedulingProperties schedulingProperties;
    private final Clock clock;

    /**
     * 정해진 시각에 수집합니다. 스케줄러가 꺼져 있으면(테스트) 아무것도 하지 않습니다.
     */
    @Scheduled(cron = "0 15 2,5,8,11,14,17,20,23 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "forecastCollect")
    public void scheduledCollect() {
        if (!schedulingProperties.enabled()) {
            return;
        }
        collect();
    }

    /**
     * 오늘 · 내일 예보를 수집해 반영합니다.
     */
    public void collect() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<DailyRain> dailyRains;
        try {
            dailyRains = kmaForecastClient.fetchDailyRain(now, policy.rainPopThreshold());
        } catch (KmaApiException e) {
            log.error("[비 예보 수집] 기상청 호출 실패로 이번 회차를 건너뜁니다: {}", e.getMessage());
            return;
        }
        if (dailyRains.isEmpty()) {
            return;
        }

        Map<LocalDate, DailyRain> byDate = dailyRains.stream()
                .collect(Collectors.toMap(DailyRain::date, Function.identity()));
        LocalDate today = now.toLocalDate();
        for (LocalDate date : List.of(today, today.plusDays(1))) {
            DailyRain rain = byDate.get(date);
            if (rain == null) {
                log.warn("[비 예보 수집] {} 예보가 응답에 없어 건너뜁니다.", date);
                continue;
            }
            try {
                forecastService.apply(
                        date, rain.maxPop(), rain.totalMm(), rain.firstRainAt(), false);
            } catch (RuntimeException e) {
                // 한 날짜 실패가 다른 날짜 반영을 막지 않게 한다
                log.error("[비 예보 수집] {} 반영 실패: {}", date, e.getMessage(), e);
            }
        }
    }
}
