package com.rainbutler.domain.forecast.job;

import com.rainbutler.domain.forecast.client.DailyRain;
import com.rainbutler.domain.forecast.client.KmaApiException;
import com.rainbutler.domain.forecast.client.KmaForecastClient;
import com.rainbutler.domain.forecast.service.ForecastService;
import com.rainbutler.domain.forecast.service.WeatherGridTargets;
import com.rainbutler.domain.request.service.RainNoticeBatch;
import com.rainbutler.global.config.PolicyProperties;
import com.rainbutler.global.geo.KmaGridConverter.Grid;
import com.rainbutler.global.scheduling.SchedulingProperties;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
 * <p>빗물받이가 있는 기상청 격자({@link WeatherGridTargets})마다 오늘 · 내일 예보를 받아 날짜마다
 * {@link ForecastService#apply}로 확정 · 갱신 · 취소합니다. 격자는 순서대로 하나씩 부르고, 한 격자의 기상청 호출이
 * 실패하면 그 격자만 이번 회차는 예보를 바꾸지 않고 다음 회차를 기다립니다 (나머지 격자는 계속). 그날 데이터가 응답에 없을 때도 바꾸지 않고
 * (데이터가 없다고 취소하지 않는다) {@link ForecastDataMissingException}을 담은 ERROR 로그를 남깁니다.
 *
 * <p>여러 격자에서 같은 사용자에게 가는 비 예보 알림은 {@link RainNoticeBatch}로 이번 회차 끝에 1개로 묶어 보냅니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ForecastCollectJob {

    private final KmaForecastClient kmaForecastClient;
    private final ForecastService forecastService;
    private final WeatherGridTargets weatherGridTargets;
    private final RainNoticeBatch rainNoticeBatch;
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
     * 모든 대상 격자의 오늘 · 내일 예보를 수집해 반영합니다.
     */
    public void collect() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Grid> grids = weatherGridTargets.find();
        long startedAt = System.currentTimeMillis();
        List<Grid> failed = new ArrayList<>();
        rainNoticeBatch.run(() -> {
            for (Grid grid : grids) {
                if (!collect(grid, now)) {
                    failed.add(grid);
                }
            }
        });
        log.info("[비 예보 수집] 격자 {}개 수집 (실패 {}개 {}), {}ms", grids.size(), failed.size(),
                failed, System.currentTimeMillis() - startedAt);
    }

    /**
     * 한 격자의 오늘 · 내일 예보를 수집해 반영합니다.
     *
     * @return 기상청 호출에 성공했으면 true
     */
    private boolean collect(Grid grid, LocalDateTime now) {
        List<DailyRain> dailyRains;
        try {
            dailyRains = kmaForecastClient.fetchDailyRain(grid, now, policy.rainPopThreshold());
        } catch (KmaApiException e) {
            log.error("[비 예보 수집] 격자 {},{} 기상청 호출 실패로 이번 회차를 건너뜁니다: {}", grid.nx(),
                    grid.ny(), e.getMessage());
            return false;
        } catch (RuntimeException e) {
            // 예상 못 한 오류도 다른 격자 수집을 막지 않게 한다
            log.error("[비 예보 수집] 격자 {},{} 수집 실패: {}", grid.nx(), grid.ny(),
                    e.getMessage(), e);
            return false;
        }
        if (dailyRains.isEmpty()) {
            return true;
        }

        Map<LocalDate, DailyRain> byDate = dailyRains.stream()
                .collect(Collectors.toMap(DailyRain::date, Function.identity(),
                        (first, second) -> first, TreeMap::new));
        LocalDate today = now.toLocalDate();
        for (LocalDate date : List.of(today, today.plusDays(1))) {
            DailyRain rain = byDate.get(date);
            if (rain == null) {
                // 데이터가 없다고 취소하면 실제로 비가 오는데 요청이 취소될 수 있다 → 상태는 그대로 두고 ERROR로 알린다
                log.error("[비 예보 수집 누락] 격자 {},{} {} 예보가 기상청 응답에 없어 이번 회차는 바꾸지 "
                        + "않습니다.", grid.nx(), grid.ny(), date,
                        new ForecastDataMissingException("격자 " + grid.nx() + "," + grid.ny()
                                + ", 누락 날짜 " + date + ", 수집 시각 " + now
                                + ", 받은 날짜 " + byDate.keySet()));
                continue;
            }
            try {
                forecastService.apply(
                        grid, date, rain.maxPop(), rain.totalMm(), rain.firstRainAt(), false);
            } catch (RuntimeException e) {
                // 한 날짜 실패가 다른 날짜 반영을 막지 않게 한다
                log.error("[비 예보 수집] 격자 {},{} {} 반영 실패: {}", grid.nx(), grid.ny(), date,
                        e.getMessage(), e);
            }
        }
        return true;
    }
}
