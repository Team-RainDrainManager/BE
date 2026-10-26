package com.rainbutler.domain.cleaning.dto.Request;

import jakarta.validation.constraints.NotBlank;

/**
 * 청소 완료 인증 요청입니다.
 *
 * <p>청소 후 사진(AFTER)이 필수입니다. 사진은 {@code POST /api/v1/uploads/presigned-url}
 * (purpose=AFTER)로 S3에 먼저 올리고, 받은 {@code key}를 {@code photoKey}로 보냅니다.
 * AI가 사진으로 등급을 판정하고, beforeGrade보다 afterGrade가 낮아야 인증에 성공합니다.
 */
public record CleaningVerifyRequest(
        @NotBlank(message = "청소 후 사진을 등록해 주세요.")
        String photoKey
) {
}
