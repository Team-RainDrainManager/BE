package com.rainbutler.domain.notification.dto;

import java.util.List;

/**
 * 알림 목록 한 페이지입니다.
 *
 * @param unreadCount 안 읽은 알림 수 (필터와 관계없이 전체)
 * @param nextCursor 다음 페이지 요청에 보낼 cursor (다음 페이지가 없으면 null)
 * @param notifications 최신순 알림
 */
public record NotificationListResponse(
        long unreadCount, Long nextCursor, List<NotificationResponse> notifications) {
}
