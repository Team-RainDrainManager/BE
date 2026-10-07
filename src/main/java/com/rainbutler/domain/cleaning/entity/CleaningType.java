package com.rainbutler.domain.cleaning.entity;

/**
 * 청소 종류입니다.
 *
 * <p>⚠️ DB에 enum 이름이 문자열로 저장됩니다. 이름을 바꾸면 기존 데이터가 깨지므로 바꾸지 마세요.
 */
public enum CleaningType {
    /** 입양 전 청소 */
    ADOPTION,
    /** 대신 점검 */
    SUBSTITUTE
}
