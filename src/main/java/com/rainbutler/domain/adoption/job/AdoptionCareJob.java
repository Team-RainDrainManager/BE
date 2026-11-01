package com.rainbutler.domain.adoption.job;

import com.rainbutler.domain.adoption.entity.AdoptionStatus;
import com.rainbutler.domain.adoption.repository.AdoptionRepository;
import com.rainbutler.domain.adoption.service.AdoptionCareResult;
import com.rainbutler.domain.adoption.service.AdoptionCareService;
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
 * 미관리 경고 · 자동 파양 배치입니다. (매일 {@code adoption-care-time}, 기본 09:00)
 *
 * <p>ACTIVE 입양마다 따로 트랜잭션을 열어 한 건 실패가 전체를 멈추지 않게 합니다.
 * 판정과 처리는 {@link AdoptionCareService}가 합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdoptionCareJob {

    private final AdoptionRepository adoptionRepository;
    private final AdoptionCareService adoptionCareService;
    private final SchedulingProperties schedulingProperties;
    private final Clock clock;

    /**
     * 정해진 시각에 실행합니다. 스케줄러가 꺼져 있으면(테스트) 아무것도 하지 않습니다.
     */
    @Scheduled(cron = "#{@scheduleCrons.adoptionCare()}", zone = "Asia/Seoul")
    @SchedulerLock(name = "adoptionCare")
    public void scheduledRun() {
        if (!schedulingProperties.enabled()) {
            return;
        }
        run();
    }

    /**
     * ACTIVE 입양을 순회하며 경고 · 자동 파양을 처리합니다.
     */
    public void run() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> adoptionIds = adoptionRepository.findIdsByStatus(AdoptionStatus.ACTIVE);
        int warned = 0;
        int released = 0;

        for (Long adoptionId : adoptionIds) {
            try {
                AdoptionCareResult result = adoptionCareService.care(adoptionId, now);
                if (result == AdoptionCareResult.WARNED) {
                    warned++;
                } else if (result == AdoptionCareResult.RELEASED) {
                    released++;
                }
            } catch (RuntimeException e) {
                log.error("[입양 관리 배치] 입양 id={} 처리 실패: {}", adoptionId, e.getMessage(), e);
            }
        }

        if (warned > 0 || released > 0) {
            log.info("[입양 관리 배치] 미관리 경고 {}건 · 자동 파양 {}건", warned, released);
        }
    }
}
