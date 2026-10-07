package com.rainbutler.domain.report.entity;

/**
 * 제보 종류입니다.
 *
 * <p>⚠️ DB에 enum 이름이 문자열로 저장됩니다. 이름을 바꾸면 기존 데이터가 깨지므로 바꾸지 마세요.
 */
public enum ReportType {
    /** 막힘 */
    BLOCKAGE,
    /** 신규 빗물받이 */
    NEW_DRAIN,
    /** 위치 수정 */
    LOCATION_FIX,
    /** 철거 */
    REMOVAL
}
