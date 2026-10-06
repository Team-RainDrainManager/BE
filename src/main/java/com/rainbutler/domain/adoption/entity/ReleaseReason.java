package com.rainbutler.domain.adoption.entity;

/**
 * 파양 사유입니다.
 *
 * <p>⚠️ DB에 enum 이름이 문자열로 저장됩니다. 이름을 바꾸면 기존 데이터가 깨지므로 바꾸지 마세요.
 */
public enum ReleaseReason {
    /** 이사 */
    MOVED,
    /** 시간 부족 */
    NO_TIME,
    /** 기타 */
    OTHER,
    /** 35일 미관리 자동 파양 */
    AUTO
}
