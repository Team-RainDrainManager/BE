package com.rainbutler.domain.notification.entity;

/**
 * 알림 종류입니다.
 *
 * <p>⚠️ DB에 enum 이름이 문자열로 저장됩니다. 이름을 바꾸면 기존 데이터가 깨지므로 바꾸지 마세요.
 */
public enum NotificationType {
    /** 내일 비 예보 - 점검 요청 */
    RAIN_TOMORROW,
    /** 당일 비 예보 - 점검 요청 (즉시 발송) */
    RAIN_TODAY,
    /**
     * 대신 점검 공개. 더 이상 발송하지 않음 (2026-11-06, 대신 점검은 지도에서 자발 참여).
     * DB에 저장된 기존 알림이 깨지지 않도록 값은 지우지 않는다
     */
    SUBSTITUTE_OPEN,
    /** 점검 요청 취소 (비 예보 취소 · 비가 일찍 와서 취소) */
    REQUEST_CANCELLED,
    /** 내 요청을 다른 사람이 대신 점검으로 맡음 */
    REQUEST_CLAIMED,
    /** 완료 마감까지 미완료 (시청 인계) */
    REQUEST_MISSED,
    /** 비가 일찍 와서 요청 마감이 당겨짐 */
    REQUEST_DEADLINE_CHANGED,
    /** 정기 관리 예정일 */
    MANAGEMENT_DUE,
    /** 미관리 경고 (28일) */
    ADOPTION_WARNING,
    /** 자동 파양 (35일) */
    ADOPTION_RELEASED,
    /** 제보 처리 결과 (승인 · 반려. 제재면 SANCTION_IMPOSED만 보낸다) */
    REPORT_RESULT,
    /** 온보딩 완료 환영 */
    WELCOME,
    /** 빗물받이 입양 완료 */
    ADOPTION_REGISTERED,
    /** 빗물받이 레벨 상승 */
    LEVEL_UP,
    /** 점검 인증 · 관리자 검토 승인 */
    INSPECTION_APPROVED,
    /** 허위 제보로 이용 제한 (제보 반려 + 제재. 이때는 제보 결과 알림을 따로 보내지 않는다) */
    SANCTION_IMPOSED
}
