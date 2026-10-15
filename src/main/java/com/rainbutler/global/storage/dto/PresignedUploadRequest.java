package com.rainbutler.global.storage.dto;

import com.rainbutler.global.storage.UploadPurpose;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 사진 업로드 URL 발급 요청입니다.
 *
 * @param purpose 사진 용도 (DRAIN · BEFORE · AFTER · REPORT)
 * @param contentType 사진 타입 (image/jpeg · image/png · image/webp)
 */
public record PresignedUploadRequest(
        @Schema(example = "DRAIN") @NotNull UploadPurpose purpose,
        @Schema(example = "image/jpeg") @NotBlank String contentType) {
}
