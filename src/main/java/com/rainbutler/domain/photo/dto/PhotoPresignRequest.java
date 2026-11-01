package com.rainbutler.domain.photo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** S3 직접 업로드 URL 발급 요청입니다. */
public record PhotoPresignRequest(
        @Schema(example = "BEFORE") @NotNull PhotoPurpose purpose,
        @Schema(example = "image/jpeg") @NotBlank String contentType) {
}
