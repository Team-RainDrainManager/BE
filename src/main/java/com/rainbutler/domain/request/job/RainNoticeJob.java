package com.rainbutler.domain.request.job;

import com.rainbutler.domain.forecast.entity.RainForecast;
import com.rainbutler.domain.forecast.repository.RainForecastRepository;
import com.rainbutler.domain.request.service.InspectionRequestService;
import com.rainbutler.domain.request.service.RequestSchedule;
import com.rainbutler.domain.request.service.RequestSchedulePolicy;
import com.rainbutler.global.scheduling.SchedulingProperties;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 비 예보 요청 일괄 발송 배치입니다. (매일 {@code rain-notice-time}, 기본 06:00)
 *
 * <ul>
 *   <li>내일 비 (실제 · 가상 각각): 기본 일정으로 발송 (응답 마감 12:00, 완료 마감 20:00)</li>
 *   <li>오늘 비: 밤사이 알게 돼 보내지 못한 예보를 당일 규칙으로 발송 (이미 보낸 빗물받이는 건너뜀)</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RainNoticeJob {

    private final RainForecastRepository rainForecastRepository;
    private final RequestSchedulePolicy schedulePolicy;
    private final InspectionRequestService inspectionRequestService;
    private final SchedulingProperties schedulingProperties;
    private final Clock clock;

    /**
     * 정해진 시각에 발송합니다. 스케줄러가 꺼져 있으면(테스트) 아무것도 하지 않습니다.
     */
    @Scheduled(cron = "#{@scheduleCrons.rainNotice()}", zone = "Asia/Seoul")
    @SchedulerLock(name = "rainNotice")
    public void scheduledRun() {
        if (!schedulingProperties.enabled()) {
            return;
        }
        run();
    }

    /**
     * 오늘 · 내일 진행 중 예보의 요청을 만들어 보냅니다.
     *
     * @return 만든 요청 수
     */
    public int run() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        int created = 0;
        for (RainForecast forecast : rainForecastRepository.findAllActiveByRainDate(
                today.plusDays(1))) {
            created += send(forecast, schedulePolicy.noticeSchedule(now));
        }
        for (RainForecast forecast : rainForecastRepository.findAllActiveByRainDate(today)) {
            created += send(forecast, schedulePolicy.plan(forecast, now));
        }
        log.info("[비 예보 일괄 발송] 요청 {}건", created);
        return created;
    }

    private int send(RainForecast forecast, RequestSchedule schedule) {
        if (!schedule.isSendNow()) {
            log.info("[비 예보 일괄 발송] 예보 id={} 보내지 않음: {}", forecast.getId(),
                    schedule.skipReason());
            return 0;
        }
        try {
            return inspectionRequestService.createForForecast(forecast.getId(), schedule);
        } catch (RuntimeException e) {
            // 한 예보 실패가 다른 예보 발송을 막지 않게 한다
            log.error("[비 예보 일괄 발송] 예보 id={} 실패: {}", forecast.getId(), e.getMessage(), e);
            return 0;
        }
    }
}
