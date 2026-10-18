package com.rainbutler.domain.user.dto;

import java.time.LocalDateTime;

/**
 * 점검 쉬기 상태입니다.
 *
 * @param canApply 지금 신청할 수 있는지
 * @param activeUntil 쉬는 중이면 종료 시각 (아니면 null)
 * @param nextAvailableAt 쿨다운 중이면 다음 신청 가능 시각 (아니면 null)
 * @param reason 신청할 수 없는 이유 (신청 가능하면 null)
 */
public record PauseStatusResponse(
        boolean canApply,
        LocalDateTime activeUntil,
        LocalDateTime nextAvailableAt,
        PauseUnavailableReason reason) {
}
