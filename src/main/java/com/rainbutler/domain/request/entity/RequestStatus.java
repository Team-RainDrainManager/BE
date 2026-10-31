package com.rainbutler.domain.request.entity;

import java.util.Set;

/**
 * 점검 요청 상태입니다.
 *
 * <p>⚠️ DB에 enum 이름이 문자열로 저장됩니다. 이름을 바꾸면 기존 데이터가 깨지므로 바꾸지 마세요.
 */
public enum RequestStatus {
    /** 입양자에게 발송됨 */
    SENT,
    /** 입양자가 수락 */
    ACCEPTED,
    /** 대신 점검 공개 */
    OPEN,
    /** 대신 점검자 확정 */
    CLAIMED,
    /** 점검 완료 */
    COMPLETED,
    /** 마감까지 미점검 */
    MISSED,
    /** 비 예보 취소 */
    CANCELLED;

    /** 진행 중인 상태 (마감 처리 · 취소 · 마감 당기기 대상) */
    public static final Set<RequestStatus> IN_PROGRESS = Set.of(SENT, ACCEPTED, OPEN, CLAIMED);
}
