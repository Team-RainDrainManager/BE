package com.rainbutler.domain.report.entity;

/**
 * 제보 처리 상태입니다.
 *
 * <p>⚠️ DB에 enum 이름이 문자열로 저장됩니다. 이름을 바꾸면 기존 데이터가 깨지므로 바꾸지 마세요.
 */
public enum ReportStatus {
    /** 처리 대기 */
    PENDING,
    /** 승인 */
    APPROVED,
    /** 반려 */
    REJECTED
}
