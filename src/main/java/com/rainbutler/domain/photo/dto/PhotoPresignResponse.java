package com.rainbutler.domain.photo.dto;

import com.rainbutler.global.storage.StorageService.PresignedUpload;
import java.time.LocalDateTime;
import java.util.Map;

/** S3 직접 업로드 URL 발급 결과입니다. */
public record PhotoPresignResponse(
        String photoKey,
        String uploadUrl,
        LocalDateTime expiresAt,
        Map<String, String> headers) {

    public static PhotoPresignResponse from(PresignedUpload upload) {
        return new PhotoPresignResponse(
                upload.key(), upload.uploadUrl(), upload.expiresAt(), upload.headers());
    }
}
