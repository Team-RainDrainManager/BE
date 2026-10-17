package com.rainbutler.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 알림 설정 변경 요청입니다. 보낸 값만 바꿉니다. (둘 다 없으면 400)
 *
 * @param notifications 활동 알림 수신 (null이면 유지)
 * @param nightConsent 야간 푸시 수신 (null이면 유지)
 */
public record NotificationSettingsUpdateRequest(
        @Schema(example = "true", nullable = true) Boolean notifications,
        @Schema(example = "false", nullable = true) Boolean nightConsent) {
}
