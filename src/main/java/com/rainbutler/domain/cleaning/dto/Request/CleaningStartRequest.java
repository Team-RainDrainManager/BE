package com.rainbutler.domain.cleaning.dto.Request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 입양 청소 시작 요청입니다.
 *
 * <p>청소 전 사진(BEFORE)과 촬영 위치가 필수입니다. 사진은 {@code POST /api/v1/uploads/presigned-url}
 * (purpose=BEFORE)로 S3에 먼저 올리고, 받은 {@code key}를 {@code photoKey}로 보냅니다.
 * AI가 사진으로 더러움 단계를 판정하고, 0등급이면 바로 입양, 1~2등급이면 청소 진행, 3등급·막힘 신고 필요면 입양 불가입니다.
 */
public record CleaningStartRequest(
        @NotBlank(message = "청소 전 사진을 등록해 주세요.")
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
