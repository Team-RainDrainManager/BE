package com.rainbutler.domain.push.service;

import com.rainbutler.domain.adoption.repository.AdoptionRepository;
import com.rainbutler.domain.notification.entity.Notification;
import com.rainbutler.domain.notification.entity.NotificationType;
import com.rainbutler.domain.notification.repository.NotificationRepository;
import com.rainbutler.domain.push.entity.FcmToken;
import com.rainbutler.domain.push.event.PushRequestedEvent;
import com.rainbutler.domain.push.repository.FcmTokenRepository;
import com.rainbutler.domain.request.repository.InspectionRequestRepository;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 알림을 저장하고 푸시를 보냅니다. 다른 도메인은 {@link #notify}만 호출합니다.
 *
 * <p>알림 행은 업무 트랜잭션과 함께 저장되고(앱 안 알림함용, 푸시 실패와 상관없이 남는다), 푸시는 그 트랜잭션이
 * <b>커밋된 뒤 비동기</b>로 보냅니다. 그래서 발송이 실패해도 업무 처리가 롤백되지 않고, 롤백된 업무의 알림은
 * 발송되지 않습니다.
 */
@Slf4j
@Service
public class PushService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final InspectionRequestRepository inspectionRequestRepository;
    private final AdoptionRepository adoptionRepository;
    private final FcmTokenRepository fcmTokenRepository;
    private final FcmSender fcmSender;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate newTransaction;

    public PushService(NotificationRepository notificationRepository,
            UserRepository userRepository,
            InspectionRequestRepository inspectionRequestRepository,
            AdoptionRepository adoptionRepository,
            FcmTokenRepository fcmTokenRepository,
            FcmSender fcmSender,
            ApplicationEventPublisher eventPublisher,
            PlatformTransactionManager transactionManager) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.inspectionRequestRepository = inspectionRequestRepository;
        this.adoptionRepository = adoptionRepository;
        this.fcmTokenRepository = fcmTokenRepository;
        this.fcmSender = fcmSender;
        this.eventPublisher = eventPublisher;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 알림을 저장하고 커밋 후 푸시를 보냅니다. 호출하는 쪽 트랜잭션 안에서 부르세요.
     *
     * @param userId 받을 사용자 id
     * @param type 알림 종류
     * @param title 알림 제목
     * @param body 알림 내용
     * @param requestId 관련 점검 요청 id (없으면 null)
     * @param adoptionId 관련 입양 id (없으면 null)
     * @param data 앱으로 함께 보낼 데이터 (없으면 null)
     * @return 저장된 알림 id
     */
    @Transactional
    public Long notify(Long userId, NotificationType type, String title, String body,
            Long requestId, Long adoptionId, Map<String, String> data) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Notification notification = notificationRepository.save(Notification.builder()
                .user(user)
                .type(type)
                .title(title)
                .body(body)
                .request(requestId == null
                        ? null : inspectionRequestRepository.getReferenceById(requestId))
                .adoption(adoptionId == null
                        ? null : adoptionRepository.getReferenceById(adoptionId))
                .build());

        Map<String, String> payload = new LinkedHashMap<>();
        if (data != null) {
            payload.putAll(data);
        }
        payload.put("type", type.name());
        payload.put("notificationId", String.valueOf(notification.getId()));
        eventPublisher.publishEvent(
                new PushRequestedEvent(notification.getId(), userId, title, body, payload));
        return notification.getId();
    }

    /**
     * 커밋 후 비동기로 푸시를 보냅니다.
     *
     * @param event 발송 요청
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(PushRequestedEvent event) {
        deliver(event);
    }

    /**
     * 푸시를 보내고 결과를 반영합니다. (동기 실행, 테스트에서 직접 호출)
     *
     * <p>예외는 밖으로 내보내지 않고 로그로 남깁니다. 발송 실패가 다른 처리에 영향을 주지 않게 하기 위해서입니다.
     *
     * @param event 발송 요청
     */
    public void deliver(PushRequestedEvent event) {
        try {
            send(event);
        } catch (Exception e) {
            log.error("[푸시 발송 오류] notificationId={} {}",
                    event.notificationId(), e.getMessage(), e);
        }
    }

    private void send(PushRequestedEvent event) throws Exception {
        if (!fcmSender.isEnabled()) {
            return;
        }
        List<String> tokens = fcmTokenRepository.findAllByUser_Id(event.userId()).stream()
                .map(FcmToken::getToken)
                .toList();
        if (tokens.isEmpty()) {
            return;
        }

        FcmSender.SendResult result =
                fcmSender.send(tokens, event.title(), event.body(), event.data());
        log.info("[푸시 발송] notificationId={} 성공 {} · 실패 {} · 무효 토큰 {}",
                event.notificationId(), result.successCount(), result.failureCount(),
                result.invalidTokens().size());

        // 비동기 스레드라 바깥 트랜잭션이 없으므로 새 트랜잭션에서 정리 · 기록한다
        newTransaction.executeWithoutResult(status -> {
            if (!result.invalidTokens().isEmpty()) {
                fcmTokenRepository.deleteAllByTokenIn(result.invalidTokens());
            }
            if (result.successCount() > 0) {
                notificationRepository.findById(event.notificationId())
                        .ifPresent(Notification::markSent);
            }
        });
    }
}
