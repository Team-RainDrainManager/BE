package com.rainbutler.global.storage.dto;

import com.rainbutler.global.storage.StorageService.PresignedUpload;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 사진 업로드 URL 발급 응답입니다.
 *
 * @param key 업로드 후 사진을 쓰는 API에 보낼 S3 key
 * @param uploadUrl 사진을 PUT으로 올릴 URL
 * @param expiresAt uploadUrl 만료 시각
 * @param headers 업로드 요청에 그대로 넣어야 하는 헤더
 */
public record PresignedUploadResponse(
        String key, String uploadUrl, LocalDateTime expiresAt, Map<String, String> headers) {

    /**
     * 발급 결과로 응답을 만듭니다.
     */
    public static PresignedUploadResponse from(PresignedUpload upload) {
        return new PresignedUploadResponse(
                upload.key(), upload.uploadUrl(), upload.expiresAt(), upload.headers());
    }
}
