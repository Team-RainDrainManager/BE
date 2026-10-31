package com.rainbutler.domain.request.service;

import com.rainbutler.domain.forecast.entity.RainForecast;
import com.rainbutler.domain.forecast.event.ForecastCancelledEvent;
import com.rainbutler.domain.forecast.event.ForecastConfirmedEvent;
import com.rainbutler.domain.forecast.repository.RainForecastRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 예보 확정 · 취소를 받아 점검 요청을 만들거나 취소합니다. (예보가 커밋된 뒤 실행)
 *
 * <p>커밋 뒤에는 원래 트랜잭션에 참여할 수 없어 새 트랜잭션(REQUIRES_NEW)에서 처리합니다. 실패해도 예보 처리(수집
 * 배치 · 가상 예보 API)는 이미 끝났으므로 ERROR 로그만 남기고 밖으로 던지지 않습니다.
 */
@Slf4j
@Component
public class ForecastRequestListener {

    private final RainForecastRepository rainForecastRepository;
    private final RequestSchedulePolicy schedulePolicy;
    private final InspectionRequestService inspectionRequestService;
    private final Clock clock;
    private final TransactionTemplate newTransaction;

    public ForecastRequestListener(RainForecastRepository rainForecastRepository,
            RequestSchedulePolicy schedulePolicy,
            InspectionRequestService inspectionRequestService,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.rainForecastRepository = rainForecastRepository;
        this.schedulePolicy = schedulePolicy;
        this.inspectionRequestService = inspectionRequestService;
        this.clock = clock;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 확정된 예보가 지금 보낼 대상(오늘 비, 낮에 알게 된 내일 비)이면 바로 요청을 만들어 보냅니다.
     * 06:00 일괄 발송 대상이면 아무것도 하지 않습니다.
     *
     * @param event 예보 확정
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onConfirmed(ForecastConfirmedEvent event) {
        try {
            newTransaction.executeWithoutResult(status -> {
                RainForecast forecast =
                        rainForecastRepository.findById(event.forecastId()).orElse(null);
                if (forecast == null) {
                    return;
                }
                RequestSchedule schedule =
                        schedulePolicy.plan(forecast, LocalDateTime.now(clock));
                if (schedule.isSendNow()) {
                    inspectionRequestService.createForForecast(event.forecastId(), schedule);
                } else {
                    log.info("[비 예보 요청] 예보 id={} 지금 보내지 않음: {} {}", event.forecastId(),
                            schedule.type(),
                            schedule.skipReason() == null ? "" : schedule.skipReason());
                }
            });
        } catch (RuntimeException e) {
            log.error("[비 예보 요청] 예보 id={} 확정 처리 실패: {}",
                    event.forecastId(), e.getMessage(), e);
        }
    }

    /**
     * 취소된 예보의 진행 중 요청을 취소합니다.
     *
     * @param event 예보 취소
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCancelled(ForecastCancelledEvent event) {
        try {
            newTransaction.executeWithoutResult(status ->
                    inspectionRequestService.cancelForForecast(event.forecastId()));
        } catch (RuntimeException e) {
            log.error("[비 예보 요청] 예보 id={} 취소 처리 실패: {}",
                    event.forecastId(), e.getMessage(), e);
        }
    }
}
