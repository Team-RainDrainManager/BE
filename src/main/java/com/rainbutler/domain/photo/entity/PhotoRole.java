package com.rainbutler.domain.photo.entity;

/**
 * 사진 용도입니다.
 *
 * <p>⚠️ DB에 enum 이름이 문자열로 저장됩니다. 이름을 바꾸면 기존 데이터가 깨지므로 바꾸지 마세요.
 */
public enum PhotoRole {
    /** 청소·점검 전 */
    BEFORE,
    /** 청소·점검 후 */
    AFTER,
    /** 제보 사진 */
    REPORT,
    /** 빗물받이 등록 사진 */
    DRAIN
}
