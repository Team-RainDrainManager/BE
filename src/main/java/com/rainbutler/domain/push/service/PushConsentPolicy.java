package com.rainbutler.domain.push.service;

import com.rainbutler.domain.user.entity.User;
import com.rainbutler.global.config.PolicyProperties;
import java.time.LocalTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 사용자에게 지금 푸시를 보내도 되는지 판단합니다.
 *
 * <ul>
 *   <li>활동 알림 수신에 동의하지 않았으면 보내지 않는다 ({@code notification_enabled})</li>
 *   <li>야간(기본 22시~08시)에는 야간 푸시에 동의한 사용자에게만 보낸다 ({@code night_push_consent})</li>
 * </ul>
 * 야간 시간대는 {@code rain-butler.policy.night-push-start-time · night-push-end-time}으로 바꿀 수 있습니다.
 */
@Component
@RequiredArgsConstructor
public class PushConsentPolicy {

    private final PolicyProperties policyProperties;

    /**
     * 지금 이 사용자에게 푸시를 보내도 되는지 확인합니다.
     *
     * @param user 받을 사용자
     * @param now 발송 시각 (한국 시간)
     * @return 발송 가능 여부
     */
    public boolean allows(User user, LocalTime now) {
        if (!user.isNotificationEnabled()) {
            return false;
        }
        return !isNightTime(now) || user.isNightPushConsent();
    }

    /**
     * 야간 시간대인지 확인합니다. 시작 시각은 포함하고 끝 시각은 포함하지 않습니다. (22:00 야간, 08:00 주간)
     *
     * @param now 확인할 시각
     * @return 야간 여부
     */
    boolean isNightTime(LocalTime now) {
        LocalTime start = policyProperties.nightPushStartTime();
        LocalTime end = policyProperties.nightPushEndTime();
        if (start.isAfter(end)) {
            // 자정을 넘는 구간 (예: 22:00 ~ 08:00)
            return !now.isBefore(start) || now.isBefore(end);
        }
        return !now.isBefore(start) && now.isBefore(end);
    }
}
