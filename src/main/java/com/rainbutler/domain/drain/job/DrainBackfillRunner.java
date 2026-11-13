package com.rainbutler.domain.drain.job;

import com.rainbutler.domain.drain.service.DrainAddressBackfillService;
import com.rainbutler.domain.drain.service.DrainWeatherAreaService;
import com.rainbutler.global.scheduling.SchedulingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 애플리케이션이 뜬 뒤 한 번, 비어 있는 빗물받이 정보(격자 · 행정동 · 주소)를 채웁니다.
 *
 * <p>카카오 호출이 많으면 오래 걸릴 수 있어 시작 스레드를 막지 않게 비동기로 돌립니다 (배포 헬스 체크 지연 방지).
 * 두 백필은 카카오 호출 제한을 넘지 않게 한 스레드에서 차례로 돕니다. 배치가 꺼진 환경(테스트)에서는 돌지 않습니다.
 * 비어 있는 행만 채우므로 다시 시작해도 안전합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DrainBackfillRunner {

    private final DrainWeatherAreaService drainWeatherAreaService;
    private final DrainAddressBackfillService drainAddressBackfillService;
    private final SchedulingProperties schedulingProperties;

    /**
     * 시작 후 백필합니다.
     */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!schedulingProperties.enabled()) {
            return;
        }
        backfillWeatherArea();
        backfillAddress();
    }

    private void backfillWeatherArea() {
        long startedAt = System.currentTimeMillis();
        try {
            DrainWeatherAreaService.BackfillResult result = drainWeatherAreaService.backfill();
            log.info("[빗물받이 날씨 지역 백필] 격자 {}건 · 행정동 {}건 채움, 행정동 실패 {}건 (다음 시작 때 다시), {}ms",
                    result.gridFilled(), result.regionFilled(), result.regionFailed(),
                    System.currentTimeMillis() - startedAt);
        } catch (RuntimeException e) {
            log.error("[빗물받이 날씨 지역 백필] 실패: {}", e.getMessage(), e);
        }
    }

    private void backfillAddress() {
        long startedAt = System.currentTimeMillis();
        try {
            DrainAddressBackfillService.BackfillResult result = drainAddressBackfillService.backfill();
            log.info("[빗물받이 주소 백필] {}건 채움, 실패 {}건 (다음 시작 때 다시), {}ms",
                    result.filled(), result.failed(), System.currentTimeMillis() - startedAt);
        } catch (RuntimeException e) {
            log.error("[빗물받이 주소 백필] 실패: {}", e.getMessage(), e);
        }
    }
}
