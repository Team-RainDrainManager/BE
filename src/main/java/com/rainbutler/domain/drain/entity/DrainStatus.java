package com.rainbutler.domain.drain.entity;

/**
 * 빗물받이 운영 상태입니다.
 *
 * <p>⚠️ DB에 enum 이름이 문자열로 저장됩니다. 이름을 바꾸면 기존 데이터가 깨지므로 바꾸지 마세요.
 */
public enum DrainStatus {
    /** 운영 중 */
    ACTIVE,
    /** 신규 제보 승인 대기 */
    PENDING,
    /** 철거됨 */
    REMOVED
}
