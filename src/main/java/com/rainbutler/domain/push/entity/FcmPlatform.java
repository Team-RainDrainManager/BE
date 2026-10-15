package com.rainbutler.domain.push.entity;

/**
 * FCM 토큰을 등록한 기기 종류입니다.
 *
 * <p>⚠️ DB에 enum 이름이 문자열로 저장됩니다. 이름을 바꾸면 기존 데이터가 깨지므로 바꾸지 마세요.
 */
public enum FcmPlatform {
    /** 웹 (PWA) */
    WEB,
    /** 안드로이드 앱 */
    ANDROID,
    /** iOS 앱 */
    IOS
}
