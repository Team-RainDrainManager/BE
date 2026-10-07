package com.rainbutler.domain.adoption.entity;

/**
 * 관리 종류입니다.
 *
 * <p>⚠️ DB에 enum 이름이 문자열로 저장됩니다. 이름을 바꾸면 기존 데이터가 깨지므로 바꾸지 마세요.
 */
public enum ManagementType {
    /** 정기 점검 */
    REGULAR,
    /** 비 예보 점검 (request 연결 필수) */
    RAIN
}
