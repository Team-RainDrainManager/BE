package com.rainbutler.domain.cleaning.entity;

/**
 * 청소 진행 상태입니다.
 *
 * <p>⚠️ DB에 enum 이름이 문자열로 저장됩니다. 이름을 바꾸면 기존 데이터가 깨지므로 바꾸지 마세요.
 */
public enum CleaningStatus {
    /** 진행 중 */
    IN_PROGRESS,
    /** AI 인증 중 */
    VERIFYING,
    /** 완료 */
    COMPLETED,
    /** 실패 */
    FAILED
}
