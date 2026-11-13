package com.rainbutler.domain.bugreport.entity;

/**
 * 버그가 난 플랫폼입니다.
 *
 * <p>⚠️ DB에 enum 이름이 문자열로 저장됩니다. 이름을 바꾸면 기존 데이터가 깨지므로 바꾸지 마세요.
 */
public enum BugReportPlatform {
    /** 웹 (PWA) */
    WEB,
    /** iOS 앱 */
    IOS,
    /** 안드로이드 앱 */
    ANDROID
}
