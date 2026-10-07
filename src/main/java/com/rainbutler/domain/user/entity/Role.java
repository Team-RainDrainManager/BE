package com.rainbutler.domain.user.entity;

/**
 * 사용자 권한입니다.
 *
 * <p>⚠️ DB에 enum 이름이 문자열로 저장됩니다. 이름을 바꾸면 기존 데이터가 깨지므로 바꾸지 마세요.
 */
public enum Role {
    /** 관리자 */
    ADMIN,
    /** 시청 관계자 */
    CITY_OFFICIAL,
    /** 일반 사용자 */
    USER
}
