package com.rainbutler.domain.request.entity;

/**
 * 입양자 응답입니다.
 *
 * <p>⚠️ DB에 enum 이름이 문자열로 저장됩니다. 이름을 바꾸면 기존 데이터가 깨지므로 바꾸지 마세요.
 */
public enum RequestReply {
    /** 가능 */
    ACCEPT,
    /** 불가 */
    DECLINE
}
