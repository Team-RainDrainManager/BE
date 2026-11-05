package com.rainbutler.domain.review.entity;

/**
 * 관리자 사진 검토 결정입니다.
 */
public enum ReviewDecision {
    /** 승인 — 사진을 인정해 점검 · 청소 · 관리 기록에 반영한다 */
    APPROVE,
    /** 반려 — 인정하지 않아 점검 수 · 봉사 시간 집계에서 뺀다 */
    REJECT
}
