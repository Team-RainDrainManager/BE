package com.rainbutler.domain.adoption.entity;

/**
 * 관리 기록 상태입니다.
 *
 * <p>⚠️ DB에 enum 이름이 문자열로 저장됩니다. 이름을 바꾸면 기존 데이터가 깨지므로 바꾸지 마세요.
 */
public enum ManagementStatus {
    /** 제출됨 */
    SUBMITTED,
    /** 인증 완료 */
    VERIFIED,
    /** 인증 실패 */
    FAILED
}
