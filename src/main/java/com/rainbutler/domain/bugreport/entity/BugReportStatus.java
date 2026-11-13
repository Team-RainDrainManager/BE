package com.rainbutler.domain.bugreport.entity;

/**
 * 버그 신고 처리 상태입니다.
 *
 * <p>⚠️ DB에 enum 이름이 문자열로 저장됩니다. 이름을 바꾸면 기존 데이터가 깨지므로 바꾸지 마세요.
 */
public enum BugReportStatus {
    /** 접수 */
    RECEIVED,
    /** 확인 중 */
    IN_PROGRESS,
    /** 해결 */
    RESOLVED,
    /** 보류 · 해결 안 함 */
    WONT_FIX;

    /**
     * 처리가 끝난 상태(해결 · 보류)인지 확인합니다.
     *
     * @return RESOLVED · WONT_FIX면 true
     */
    public boolean isClosed() {
        return this == RESOLVED || this == WONT_FIX;
    }
}
