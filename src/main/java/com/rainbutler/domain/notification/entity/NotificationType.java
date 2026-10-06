package com.rainbutler.domain.notification.entity;

/**
 * 알림 종류입니다.
 *
 * <p>⚠️ DB에 enum 이름이 문자열로 저장됩니다. 이름을 바꾸면 기존 데이터가 깨지므로 바꾸지 마세요.
 */
public enum NotificationType {
    /** 내일 비 예보 - 점검 요청 */
    RAIN_TOMORROW,
    /** 대신 점검 공개 */
    SUBSTITUTE_OPEN,
    /** 점검 요청 취소 (비 예보 취소) */
    REQUEST_CANCELLED,
    /** 정기 관리 예정일 */
    MANAGEMENT_DUE,
    /** 미관리 경고 (28일) */
    ADOPTION_WARNING,
    /** 자동 파양 (35일) */
    ADOPTION_RELEASED,
    /** 제보 처리 결과 */
    REPORT_RESULT
}
