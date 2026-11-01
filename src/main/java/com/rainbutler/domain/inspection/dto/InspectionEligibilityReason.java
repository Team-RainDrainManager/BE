package com.rainbutler.domain.inspection.dto;

/** 점검을 시작할 수 없는 사유입니다. */
public enum InspectionEligibilityReason {
    /** 현재 제재 중인 사용자 */
    SANCTIONED,
    /** 정기 점검 입양자 또는 요청 수락자가 아님 */
    NOT_ASSIGNED,
    /** 요청이 종료됐거나 완료 마감이 지남 */
    REQUEST_CLOSED,
    /** 비 예보 요청에 아직 가능 응답을 하지 않음 */
    REPLY_REQUIRED
}
