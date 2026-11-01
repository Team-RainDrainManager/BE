package com.rainbutler.domain.photo.dto;

import com.rainbutler.global.storage.UploadPurpose;

/** 점검·청소·제보 사진의 업로드 용도입니다. */
public enum PhotoPurpose {
    /** 점검 또는 청소 전 사진 */
    BEFORE,
    /** 청소 후 사진 */
    AFTER,
    /** 제보 사진 */
    REPORT;

    public UploadPurpose toUploadPurpose() {
        return UploadPurpose.valueOf(name());
    }
}
