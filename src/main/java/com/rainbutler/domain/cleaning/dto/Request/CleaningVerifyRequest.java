package com.rainbutler.domain.cleaning.dto.Request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 청소 완료 인증 요청입니다.
 *
 * <p>청소 후 사진(AFTER)과 촬영 위치가 필수입니다. 사진은 {@code POST /api/v1/uploads/presigned-url}
 * (purpose=AFTER)로 S3에 먼저 올리고, 받은 {@code key}를 {@code photoKey}로 보냅니다.
 * AI가 사진으로 등급을 판정하고, beforeGrade보다 afterGrade가 낮아야 인증에 성공합니다.
 * 촬영 위치는 빗물받이와 가까워야 하고, 점검 전 사진을 찍은 위치와도 멀어지면 안 됩니다.
 */
public record CleaningVerifyRequest(
        @NotBlank(message = "청소 후 사진을 등록해 주세요.")
        String photoKey,

        @NotNull(message = "촬영 위도를 입력해 주세요.")
        @DecimalMin(value = "-90.0", message = "위도는 -90 이상 90 이하여야 합니다.")
        @DecimalMax(value = "90.0", message = "위도는 -90 이상 90 이하여야 합니다.")
        Double latitude,

        @NotNull(message = "촬영 경도를 입력해 주세요.")
        @DecimalMin(value = "-180.0", message = "경도는 -180 이상 180 이하여야 합니다.")
        @DecimalMax(value = "180.0", message = "경도는 -180 이상 180 이하여야 합니다.")
        Double longitude
) {
}
