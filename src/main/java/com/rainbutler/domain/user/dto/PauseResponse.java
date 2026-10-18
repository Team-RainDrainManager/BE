package com.rainbutler.domain.user.dto;

import java.time.LocalDateTime;

/**
 * 점검 쉬기 신청 결과입니다.
 *
 * @param pauseUntil 쉬기 종료 시각
 */
public record PauseResponse(LocalDateTime pauseUntil) {
}
