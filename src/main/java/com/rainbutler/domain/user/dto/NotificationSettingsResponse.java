package com.rainbutler.domain.user.dto;

import com.rainbutler.domain.user.entity.User;

/**
 * 알림 설정입니다.
 *
 * @param notifications 활동 알림 수신 (끄면 알림을 만들지도 보내지도 않는다)
 * @param nightConsent 야간(22시~08시) 푸시 수신 (끄면 야간에는 푸시 없이 알림함에만 저장)
 */
public record NotificationSettingsResponse(boolean notifications, boolean nightConsent) {

    /**
     * 사용자 엔티티로 응답을 만듭니다.
     */
    public static NotificationSettingsResponse from(User user) {
        return new NotificationSettingsResponse(
                user.isNotificationEnabled(), user.isNightPushConsent());
    }
}
