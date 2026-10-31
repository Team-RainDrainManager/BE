package com.rainbutler.domain.request.job;

import com.rainbutler.domain.notification.entity.NotificationType;
import com.rainbutler.domain.push.service.PushService;
import com.rainbutler.domain.request.entity.InspectionRequest;
import com.rainbutler.domain.request.entity.RequestStatus;
import com.rainbutler.domain.request.repository.InspectionRequestRepository;
import com.rainbutler.domain.request.service.SubstituteNotifier;
import com.rainbutler.global.scheduling.SchedulingProperties;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 비 예보 요청 마감 처리 배치입니다. (5분마다)
 *
 * <p>당일 예보처럼 마감 시각이 요청마다 달라서 고정 시각이 아니라 주기적으로 확인합니다.
 * <ol>
 *   <li><b>완료 마감 지남</b>: 진행 중(SENT · ACCEPTED · OPEN · CLAIMED) → {@code MISSED}, 입양자에게
 *       "시청에 전달됐어요" 알림 (불이익 없음)</li>
 *   <li><b>응답 마감 지남</b>: {@code SENT} → {@code OPEN}(대신 점검 공개) + 연속 무응답 횟수 1 증가 +
 *       근처 입양자 알림</li>
 * </ol>
 * 완료 마감을 먼저 처리해 두 마감이 함께 지난 요청은 바로 MISSED가 되게 합니다 (쓸모없는 대신 점검 알림 방지).
 * 요청마다 따로 커밋해 한 건 실패가 전체를 멈추지 않게 하고, 한 번에 최대 500건씩 처리합니다.
 */
@Slf4j
@Component
public class RequestDeadlineJob {

    private static final int BATCH_SIZE = 500;

    private final InspectionRequestRepository inspectionRequestRepository;
    private final SubstituteNotifier substituteNotifier;
    private final PushService pushService;
    private final SchedulingProperties schedulingProperties;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public RequestDeadlineJob(InspectionRequestRepository inspectionRequestRepository,
            SubstituteNotifier substituteNotifier,
            PushService pushService,
            SchedulingProperties schedulingProperties,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.inspectionRequestRepository = inspectionRequestRepository;
        this.substituteNotifier = substituteNotifier;
        this.pushService = pushService;
        this.schedulingProperties = schedulingProperties;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * 5분마다 마감을 처리합니다. 스케줄러가 꺼져 있으면(테스트) 아무것도 하지 않습니다.
     */
    @Scheduled(cron = "0 */5 * * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "requestDeadline")
    public void scheduledRun() {
        if (!schedulingProperties.enabled()) {
            return;
        }
        run();
    }

    /**
     * 완료 마감 → 응답 마감 순으로 처리합니다.
     */
    public void run() {
        LocalDateTime now = LocalDateTime.now(clock);
        int missed = processCompleteOverdue(now);
        int opened = processReplyOverdue(now);
        if (missed > 0 || opened > 0) {
            log.info("[요청 마감 처리] 놓침 {}건 · 대신 점검 공개 {}건", missed, opened);
        }
    }

    private int processCompleteOverdue(LocalDateTime now) {
        List<Long> ids = inspectionRequestRepository.findIdsCompleteOverdue(
                RequestStatus.IN_PROGRESS, now, PageRequest.of(0, BATCH_SIZE));
        int missed = 0;
        for (Long id : ids) {
            try {
                Boolean done = transaction.execute(status -> miss(id, now));
                if (Boolean.TRUE.equals(done)) {
                    missed++;
                }
            } catch (RuntimeException e) {
                log.error("[요청 마감 처리] 완료 마감 처리 실패 requestId={}: {}", id, e.getMessage(), e);
            }
        }
        return missed;
    }

    private boolean miss(Long id, LocalDateTime now) {
        InspectionRequest request = inspectionRequestRepository.findById(id).orElse(null);
        if (request == null || !request.isInProgress()
                || request.getCompleteDeadlineAt().isAfter(now)) {
            return false;
        }
        request.miss(now);
        if (request.getAdoption() != null) {
            pushService.notify(request.getAdoption().getUser().getId(),
                    NotificationType.REQUEST_MISSED, "이번 비 예보 점검은 시청에 전달됐어요",
                    "마감까지 점검하지 못한 빗물받이는 시청이 확인해요. 불이익은 없어요.",
                    request.getId(), request.getAdoption().getId(),
                    Map.of("requestId", String.valueOf(request.getId())));
        }
        return true;
    }

    private int processReplyOverdue(LocalDateTime now) {
        List<Long> ids = inspectionRequestRepository.findIdsReplyOverdue(
                now, PageRequest.of(0, BATCH_SIZE));
        List<Long> opened = new ArrayList<>();
        for (Long id : ids) {
            try {
                Boolean done = transaction.execute(status -> open(id, now));
                if (Boolean.TRUE.equals(done)) {
                    opened.add(id);
                }
            } catch (RuntimeException e) {
                log.error("[요청 마감 처리] 응답 마감 처리 실패 requestId={}: {}", id, e.getMessage(), e);
            }
        }
        if (!opened.isEmpty()) {
            try {
                // 같은 사용자에게 여러 건이면 알림 1개로 묶이도록 한 번에 보낸다
                transaction.executeWithoutResult(status -> substituteNotifier.notifyNearbyAdopters(
                        inspectionRequestRepository.findAllById(opened)));
            } catch (RuntimeException e) {
                log.error("[요청 마감 처리] 대신 점검 알림 실패: {}", e.getMessage(), e);
            }
        }
        return opened.size();
    }

    private boolean open(Long id, LocalDateTime now) {
        InspectionRequest request = inspectionRequestRepository.findById(id).orElse(null);
        if (request == null || request.getStatus() != RequestStatus.SENT
                || request.getReplyDeadlineAt().isAfter(now)) {
            return false;
        }
        request.open(now);
        if (request.getAdoption() != null) {
            request.getAdoption().increaseIgnoreCount();
        }
        return true;
    }
}
