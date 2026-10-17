package com.rainbutler.domain.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.google.firebase.messaging.FirebaseMessagingException;
import com.rainbutler.domain.notification.entity.Notification;
import com.rainbutler.domain.notification.entity.NotificationType;
import com.rainbutler.domain.notification.repository.NotificationRepository;
import com.rainbutler.domain.push.entity.FcmPlatform;
import com.rainbutler.domain.push.entity.FcmToken;
import com.rainbutler.domain.push.event.PushRequestedEvent;
import com.rainbutler.domain.push.repository.FcmTokenRepository;
import com.rainbutler.domain.push.service.FcmSender;
import com.rainbutler.domain.push.service.PushService;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.support.IntegrationTestSupport;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 알림 저장 · 푸시 발송을 검증합니다. FcmSender는 mock이라 Firebase 없이 동작합니다.
 *
 * <p>{@code handle}은 {@code @Async}라 직접 불러도 다른 스레드에서 돈다. 결과가 흔들리지 않도록 발송 로직은
 * 동기 메서드 {@code deliver}를 직접 호출해 검증합니다.
 */
@RecordApplicationEvents
class PushServiceTest extends IntegrationTestSupport {

    @Autowired
    private PushService pushService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private FcmTokenRepository fcmTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ApplicationEvents events;

    @MockitoBean
    private FcmSender fcmSender;

    /** 발송 시각을 고정한다 (기본: 낮 14시) */
    @MockitoBean
    private Clock clock;

    @BeforeEach
    void setUp() {
        fixClock(14, 0);
    }

    @Test
    void notify는_알림을_저장하고_type과_notificationId를_담은_발송_이벤트를_발행한다() {
        User user = saveUser();

        Long notificationId = transactionTemplate.execute(status -> pushService.notify(
                user.getId(), NotificationType.RAIN_TOMORROW, "내일 비 소식", "빗물받이를 확인해 주세요",
                null, null, Map.of("drainId", "7")));

        assertThat(notificationRepository.findById(notificationId)).isPresent();
        PushRequestedEvent event =
                events.stream(PushRequestedEvent.class).findFirst().orElseThrow();
        assertThat(event.userId()).isEqualTo(user.getId());
        assertThat(event.data())
                .containsEntry("type", "RAIN_TOMORROW")
                .containsEntry("notificationId", String.valueOf(notificationId))
                .containsEntry("drainId", "7");
    }

    @Test
    void 트랜잭션이_커밋되면_비동기로_발송한다() throws Exception {
        User user = saveUser();
        String token = saveToken(user);
        given(fcmSender.isEnabled()).willReturn(true);
        given(fcmSender.send(anyList(), anyString(), anyString(), anyMap()))
                .willReturn(new FcmSender.SendResult(1, 0, List.of()));

        transactionTemplate.executeWithoutResult(status -> pushService.notify(user.getId(),
                NotificationType.SUBSTITUTE_OPEN, "대신 점검 공개", "근처 빗물받이를 대신 점검해 주세요",
                null, null, null));

        verify(fcmSender, timeout(5000)).send(eq(List.of(token)), eq("대신 점검 공개"),
                anyString(), anyMap());
    }

    @Test
    void 트랜잭션이_롤백되면_발송하지_않는다() throws Exception {
        User user = saveUser();
        saveToken(user);
        given(fcmSender.isEnabled()).willReturn(true);

        transactionTemplate.executeWithoutResult(status -> {
            pushService.notify(user.getId(), NotificationType.ADOPTION_WARNING, "관리 경고",
                    "28일째 관리하지 않았어요", null, null, null);
            status.setRollbackOnly();
        });

        verify(fcmSender, after(1500).never()).send(anyList(), anyString(), anyString(), anyMap());
    }

    @Test
    void 발송_성공이_있으면_sent_at을_기록하고_무효_토큰은_삭제한다() throws Exception {
        User user = saveUser();
        String valid = saveToken(user);
        String invalid = saveToken(user);
        Notification notification = saveNotification(user);
        given(fcmSender.isEnabled()).willReturn(true);
        given(fcmSender.send(anyList(), anyString(), anyString(), anyMap()))
                .willReturn(new FcmSender.SendResult(1, 1, List.of(invalid)));

        pushService.deliver(event(notification, user));

        assertThat(fcmTokenRepository.findByToken(invalid)).isEmpty();
        assertThat(fcmTokenRepository.findByToken(valid)).isPresent();
        assertThat(notificationRepository.findById(notification.getId()).orElseThrow().getSentAt())
                .isNotNull();
    }

    @Test
    void 모두_실패하면_sent_at을_기록하지_않는다() throws Exception {
        User user = saveUser();
        saveToken(user);
        Notification notification = saveNotification(user);
        given(fcmSender.isEnabled()).willReturn(true);
        given(fcmSender.send(anyList(), anyString(), anyString(), anyMap()))
                .willReturn(new FcmSender.SendResult(0, 1, List.of()));

        pushService.deliver(event(notification, user));

        assertThat(notificationRepository.findById(notification.getId()).orElseThrow().getSentAt())
                .isNull();
    }

    @Test
    void Firebase가_설정되지_않았으면_발송하지_않는다() throws Exception {
        User user = saveUser();
        saveToken(user);
        Notification notification = saveNotification(user);
        given(fcmSender.isEnabled()).willReturn(false);

        pushService.deliver(event(notification, user));

        verify(fcmSender, never()).send(anyList(), anyString(), anyString(), anyMap());
        assertThat(notificationRepository.findById(notification.getId()).orElseThrow().getSentAt())
                .isNull();
    }

    @Test
    void 알림_수신에_동의하지_않은_사용자에게는_보내지_않고_알림함에만_남긴다() throws Exception {
        User user = saveUser(false, true);
        saveToken(user);
        Notification notification = saveNotification(user);
        given(fcmSender.isEnabled()).willReturn(true);

        pushService.deliver(event(notification, user));

        verify(fcmSender, never()).send(anyList(), anyString(), anyString(), anyMap());
        assertThat(notificationRepository.findById(notification.getId())).isPresent()
                .get().extracting(Notification::getSentAt).isNull();
    }

    @Test
    void 야간에는_야간_푸시에_동의한_사용자에게만_보낸다() throws Exception {
        fixClock(23, 30);
        User nightOff = saveUser(true, false);
        User nightOn = saveUser(true, true);
        saveToken(nightOff);
        String nightOnToken = saveToken(nightOn);
        given(fcmSender.isEnabled()).willReturn(true);
        given(fcmSender.send(anyList(), anyString(), anyString(), anyMap()))
                .willReturn(new FcmSender.SendResult(1, 0, List.of()));

        pushService.deliver(event(saveNotification(nightOff), nightOff));
        pushService.deliver(event(saveNotification(nightOn), nightOn));

        verify(fcmSender).send(eq(List.of(nightOnToken)), anyString(), anyString(), anyMap());
    }

    @Test
    void 토큰이_없으면_발송하지_않는다() throws Exception {
        User user = saveUser();
        Notification notification = saveNotification(user);
        given(fcmSender.isEnabled()).willReturn(true);

        pushService.deliver(event(notification, user));

        verify(fcmSender, never()).send(anyList(), anyString(), anyString(), anyMap());
    }

    @Test
    void 발송_중_예외가_나도_밖으로_던지지_않는다() throws Exception {
        User user = saveUser();
        String token = saveToken(user);
        Notification notification = saveNotification(user);
        given(fcmSender.isEnabled()).willReturn(true);
        given(fcmSender.send(eq(List.of(token)), anyString(), anyString(), anyMap()))
                .willThrow(mock(FirebaseMessagingException.class));

        assertThatCode(() -> pushService.deliver(event(notification, user)))
                .doesNotThrowAnyException();
        assertThat(fcmTokenRepository.findByToken(token)).isPresent();
    }

    private PushRequestedEvent event(Notification notification, User user) {
        return new PushRequestedEvent(notification.getId(), user.getId(), notification.getTitle(),
                notification.getBody(), Map.of("type", notification.getType().name()));
    }

    /** 알림 · 야간 푸시 모두 동의한 사용자 */
    private User saveUser() {
        return saveUser(true, true);
    }

    private User saveUser(boolean notificationEnabled, boolean nightPushConsent) {
        User user = User.local(UUID.randomUUID() + "@test.com", "{noop}irrelevant", "푸시집사");
        user.completeOnboarding(LocalDateTime.now(), notificationEnabled, nightPushConsent);
        return userRepository.save(user);
    }

    private void fixClock(int hour, int minute) {
        ZoneId seoul = ZoneId.of("Asia/Seoul");
        given(clock.getZone()).willReturn(seoul);
        given(clock.instant()).willReturn(LocalDateTime.of(2026, 10, 17, hour, minute)
                .atZone(seoul).toInstant());
    }

    private String saveToken(User user) {
        String token = "fcm-" + UUID.randomUUID();
        fcmTokenRepository.save(FcmToken.builder()
                .user(user).token(token).platform(FcmPlatform.WEB).build());
        return token;
    }

    private Notification saveNotification(User user) {
        // 이벤트 없이 알림 행만 만든다 (비동기 발송이 테스트 중에 끼어들지 않게)
        return notificationRepository.save(Notification.builder()
                .user(user)
                .type(NotificationType.MANAGEMENT_DUE)
                .title("관리 예정일")
                .body("오늘은 빗물받이를 살펴보는 날이에요")
                .build());
    }
}
