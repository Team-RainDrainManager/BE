package com.rainbutler.domain.push.job;

import com.rainbutler.domain.notification.entity.Notification;
import com.rainbutler.domain.notification.repository.NotificationRepository;
import com.rainbutler.domain.push.service.PushService;
import com.rainbutler.global.config.PolicyProperties;
import com.rainbutler.global.scheduling.SchedulingProperties;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 야간 푸시 미동의자에게 밤사이 쌓인 알림을 아침에 푸시합니다. (매일 {@code night-deferred-push-time}, 기본 09:00)
 *
 * <p>야간(22:00 ~ 08:00)에 생긴 알림은 미동의자에게 알림함에만 저장되고 푸시되지 않습니다 (06:00 비 예보 알림 등).
 * 이 배치가 어제 22:00 ~ 오늘 08:00 사이에 만들어져 아직 푸시하지 않은 알림을 사용자별로 묶어 한 번 보냅니다.
 * <ul>
 *   <li>1건이면 그 알림의 제목 · 내용</li>
 *   <li>여러 건이면 "밤사이 알림 N건이 있어요"</li>
 * </ul>
 * 알림 켬 + 야간 미동의 사용자만 대상입니다. 비 예보 알림만이 아니라 모든 알림에 적용됩니다.
 */
@Slf4j
@Component
public class DeferredNightPushJob {

    private final NotificationRepository notificationRepository;
    private final PushService pushService;
    private final PolicyProperties policy;
    private final SchedulingProperties schedulingProperties;
    private final Clock clock;
    private final TransactionTemplate readOnly;

    public DeferredNightPushJob(NotificationRepository notificationRepository,
            PushService pushService,
            PolicyProperties policy,
            SchedulingProperties schedulingProperties,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.notificationRepository = notificationRepository;
        this.pushService = pushService;
        this.policy = policy;
        this.schedulingProperties = schedulingProperties;
        this.clock = clock;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    /**
     * 정해진 시각에 보냅니다. 스케줄러가 꺼져 있으면(테스트) 아무것도 하지 않습니다.
     */
    @Scheduled(cron = "#{@scheduleCrons.nightDeferredPush()}", zone = "Asia/Seoul")
    @SchedulerLock(name = "deferredNightPush")
    public void scheduledRun() {
        if (!schedulingProperties.enabled()) {
            return;
        }
        run();
    }

    /**
     * 어제 야간 시작 ~ 오늘 야간 끝 사이의 미발송 알림을 사용자별로 묶어 푸시합니다.
     *
     * @return 푸시를 보낸 사용자 수
     */
    public int run() {
        LocalDate today = LocalDateTime.now(clock).toLocalDate();
        LocalDateTime from = today.minusDays(1).atTime(policy.nightPushStartTime());
        LocalDateTime to = today.atTime(policy.nightPushEndTime());

        Map<Long, List<Pending>> byUser = readOnly.execute(status -> {
            Map<Long, List<Pending>> grouped = new LinkedHashMap<>();
            for (Notification n : notificationRepository.findDeferredNightNotifications(from, to)) {
                grouped.computeIfAbsent(n.getUser().getId(), id -> new ArrayList<>())
                        .add(new Pending(n.getId(), n.getType().name(), n.getTitle(), n.getBody(),
                                n.getRequest() == null ? null : n.getRequest().getId()));
            }
            return grouped;
        });

        int sent = 0;
        for (Map.Entry<Long, List<Pending>> entry : byUser.entrySet()) {
            if (deliver(entry.getKey(), entry.getValue())) {
                sent++;
            }
        }
        log.info("[밤사이 알림 푸시] 대상 {}명 · 발송 {}명", byUser.size(), sent);
        return sent;
    }

    private boolean deliver(Long userId, List<Pending> pending) {
        List<Long> ids = pending.stream().map(Pending::notificationId).toList();
        Map<String, String> data = new LinkedHashMap<>();
        if (pending.size() == 1) {
            Pending only = pending.get(0);
            data.put("type", only.type());
            data.put("notificationId", String.valueOf(only.notificationId()));
            if (only.requestId() != null) {
                data.put("requestId", String.valueOf(only.requestId()));
            }
            return pushService.deliverDeferred(userId, ids, only.title(), only.body(), data);
        }
        data.put("type", "NIGHT_SUMMARY");
        data.put("count", String.valueOf(pending.size()));
        return pushService.deliverDeferred(userId, ids, "밤사이 알림 " + pending.size() + "건이 있어요",
                "앱에서 확인해 주세요.", data);
    }

    /** 푸시할 알림 요약입니다. */
    private record Pending(
            Long notificationId, String type, String title, String body, Long requestId) {
    }
}
