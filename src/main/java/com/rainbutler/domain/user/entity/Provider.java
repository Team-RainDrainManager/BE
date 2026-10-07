package com.rainbutler.domain.user.entity;

/**
 * 소셜 로그인 제공자입니다.
 *
 * <p>⚠️ DB에 enum 이름이 문자열로 저장됩니다. 이름을 바꾸면 기존 데이터가 깨지므로 바꾸지 마세요.
 */
public enum Provider {
    /** 카카오 */
    KAKAO
}
