package com.rainbutler.domain.activity.entity;

/**
 * 활동 내역 종류입니다.
 *
 * <p>점검 기록 · 제보 기록 · 경험치(포인트) 내역을 한 목록에 모아 보여 줄 때 씁니다.
 */
public enum ActivityType {
    /** 점검 기록 */
    INSPECTION,
    /** 제보 기록 */
    REPORT,
    /** 경험치(포인트) 내역 */
    POINT
}
