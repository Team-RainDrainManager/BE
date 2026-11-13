package com.rainbutler.domain.bugreport.entity;

/**
 * 버그 신고 분류입니다.
 *
 * <p>⚠️ DB에 enum 이름이 문자열로 저장됩니다. 이름을 바꾸면 기존 데이터가 깨지므로 바꾸지 마세요.
 */
public enum BugReportCategory {
    /** 오류 · 멈춤 */
    ERROR,
    /** 화면 깨짐 · 표시 이상 */
    UI,
    /** 기능이 안 됨 */
    FEATURE,
    /** 느림 */
    PERFORMANCE,
    /** 기타 */
    OTHER
}
