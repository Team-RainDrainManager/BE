package com.rainbutler.domain.notification.entity;

import java.util.List;

/**
 * 알림 화면의 탭 구분입니다.
 *
 * <p>점검 요청 탭에는 비 예보 · 대신 점검 · 요청 상태 알림이, 활동 알림 탭에는 입양 · 성장 · 점검 승인 · 제보
 * 결과 · 제재 알림이 모입니다.
 */
public enum NotificationCategory {
    /** 점검 요청 */
    REQUEST("점검 요청"),
    /** 활동 알림 */
    ACTIVITY("활동 알림");

    private final String label;

    NotificationCategory(String label) {
        this.label = label;
    }

    /**
     * 탭에 표시할 한국어 이름입니다.
     *
     * @return 탭 이름
     */
    public String getLabel() {
        return label;
    }

    /**
     * 알림 종류가 속한 탭을 구합니다.
     *
     * @param type 알림 종류
     * @return 탭 구분
     */
    public static NotificationCategory of(NotificationType type) {
        return switch (type) {
            case RAIN_TOMORROW, RAIN_TODAY, SUBSTITUTE_OPEN, REQUEST_CANCELLED,
                    REQUEST_CLAIMED, REQUEST_MISSED, REQUEST_DEADLINE_CHANGED -> REQUEST;
            case MANAGEMENT_DUE, ADOPTION_WARNING, ADOPTION_RELEASED, REPORT_RESULT,
                    WELCOME, ADOPTION_REGISTERED, LEVEL_UP, INSPECTION_APPROVED,
                    SANCTION_IMPOSED -> ACTIVITY;
        };
    }

    /**
     * 탭에 속한 알림 종류 목록입니다. (안 읽은 수 조회에 쓴다)
     *
     * @return 알림 종류 목록
     */
    public List<NotificationType> types() {
        return List.of(NotificationType.values()).stream()
                .filter(type -> of(type) == this)
                .toList();
    }
}
