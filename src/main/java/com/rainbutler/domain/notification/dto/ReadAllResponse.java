package com.rainbutler.domain.notification.dto;

/**
 * 모두 읽음 결과입니다.
 *
 * @param updatedCount 이번에 읽음으로 바뀐 알림 수
 */
public record ReadAllResponse(int updatedCount) {
}
