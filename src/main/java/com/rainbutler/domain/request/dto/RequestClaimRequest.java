package com.rainbutler.domain.request.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * 대신 점검 수락 요청입니다. 앱을 쓰는 중의 현재 위치이며 저장하지 않습니다.
 *
 * @param latitude 현재 위도
 * @param longitude 현재 경도
 */
public record RequestClaimRequest(
        @Schema(example = "37.4855") @NotNull @DecimalMin("-90") @DecimalMax("90")
        Double latitude,
        @Schema(example = "126.8115") @NotNull @DecimalMin("-180") @DecimalMax("180")
        Double longitude) {
}
