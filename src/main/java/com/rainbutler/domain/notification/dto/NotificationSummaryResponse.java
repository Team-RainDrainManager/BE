package com.rainbutler.domain.notification.dto;

/**
 * 알림함 탭별 안 읽은 알림 수입니다.
 *
 * @param totalUnread 전체 안 읽은 알림 수
 * @param requestUnread 점검 요청 탭의 안 읽은 알림 수
 * @param activityUnread 활동 알림 탭의 안 읽은 알림 수
 */
public record NotificationSummaryResponse(
        long totalUnread,
        long requestUnread,
        long activityUnread) {
}
