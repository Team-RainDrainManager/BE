package com.rainbutler.global.entity;

/**
 * 시청 검토 상태입니다. Cleaning과 ManagementLog가 같이 씁니다.
 *
 * <p>⚠️ DB에 enum 이름이 문자열로 저장됩니다. 이름을 바꾸면 기존 데이터가 깨지므로 바꾸지 마세요.
 */
public enum ReviewStatus {
    /** 검토 불필요 (AI 인증만으로 처리) */
    NOT_REQUIRED,
    /** 검토 대기 */
    PENDING,
    /** 승인 */
    APPROVED,
    /** 반려 */
    REJECTED
}
