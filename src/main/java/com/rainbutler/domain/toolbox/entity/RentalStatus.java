package com.rainbutler.domain.toolbox.entity;

/**
 * 도구 대여 상태입니다.
 *
 * <p>⚠️ DB에 enum 이름이 문자열로 저장됩니다. 이름을 바꾸면 기존 데이터가 깨지므로 바꾸지 마세요.
 */
public enum RentalStatus {
    /** 대여 중 */
    RENTED,
    /** 반납됨 */
    RETURNED
}
