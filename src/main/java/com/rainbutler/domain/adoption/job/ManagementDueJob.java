package com.rainbutler.domain.adoption.job;

import com.rainbutler.domain.adoption.entity.AdoptionStatus;
import com.rainbutler.domain.adoption.repository.AdoptionRepository;
import com.rainbutler.domain.adoption.service.ManagementDueService;
import com.rainbutler.global.scheduling.SchedulingProperties;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 관리 예정일 하루 전 알림 배치입니다. (매일 {@code adoption-care-time}, 기본 09:00)
 *
 * <p>미관리 경고 · 자동 파양 배치와 같은 시각에 돌지만, 서로 다른 ShedLock을 써서 한쪽이 실패해도 다른 쪽은
 * 실행됩니다. ACTIVE 입양마다 따로 트랜잭션을 열어 한 건 실패가 전체를 멈추지 않게 합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ManagementDueJob {

    private final AdoptionRepository adoptionRepository;
    private final ManagementDueService managementDueService;
    private final SchedulingProperties schedulingProperties;
    private final Clock clock;

    /**
     * 정해진 시각에 실행합니다. 스케줄러가 꺼져 있으면(테스트) 아무것도 하지 않습니다.
     */
    @Scheduled(cron = "#{@scheduleCrons.adoptionCare()}", zone = "Asia/Seoul")
    @SchedulerLock(name = "managementDue")
    public void scheduledRun() {
        if (!schedulingProperties.enabled()) {
            return;
        }
        run();
    }

    /**
     * ACTIVE 입양을 순회하며 관리 예정일 하루 전 알림을 보냅니다.
     */
    public void run() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> adoptionIds = adoptionRepository.findIdsByStatus(AdoptionStatus.ACTIVE);
        int notified = 0;

        for (Long adoptionId : adoptionIds) {
            try {
                if (managementDueService.notifyDue(adoptionId, now)) {
                    notified++;
                }
            } catch (RuntimeException e) {
                log.error("[관리 예정 알림 배치] 입양 id={} 처리 실패: {}", adoptionId, e.getMessage(), e);
            }
        }

        if (notified > 0) {
            log.info("[관리 예정 알림 배치] 관리 예정일 하루 전 알림 {}건", notified);
        }
    }
}
