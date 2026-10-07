package com.rainbutler.domain.drain.entity;

/**
 * 빗물받이 입양 상태입니다.
 *
 * <p>⚠️ DB에 enum 이름이 문자열로 저장됩니다. 이름을 바꾸면 기존 데이터가 깨지므로 바꾸지 마세요.
 */
public enum DrainAdoptionStatus {
    /** 입양 가능 */
    AVAILABLE,
    /** 입양됨 */
    ADOPTED
}
