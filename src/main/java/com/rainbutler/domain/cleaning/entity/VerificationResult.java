package com.rainbutler.domain.cleaning.entity;

/**
 * AI 인증 결과입니다.
 *
 * <p>⚠️ DB에 enum 이름이 문자열로 저장됩니다. 이름을 바꾸면 기존 데이터가 깨지므로 바꾸지 마세요.
 */
public enum VerificationResult {
    /** 통과 */
    PASS,
    /** 실패 */
    FAIL
}
