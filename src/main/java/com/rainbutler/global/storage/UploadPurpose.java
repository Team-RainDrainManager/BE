package com.rainbutler.global.storage;

import java.util.Locale;

/**
 * 사진 용도입니다. S3 key의 두 번째 경로로 소문자가 들어갑니다. (예: {@code prod/before/12/...})
 */
public enum UploadPurpose {
    /** 현장 조사 · 빗물받이 등록 사진 */
    DRAIN,
    /** 청소 · 점검 전 사진 */
    BEFORE,
    /** 청소 · 점검 후 사진 */
    AFTER,
    /** 제보 사진 */
    REPORT;

    /**
     * S3 key에 들어가는 경로 이름입니다.
     *
     * @return 소문자 용도 이름
     */
    public String pathSegment() {
        return name().toLowerCase(Locale.ROOT);
    }
}
