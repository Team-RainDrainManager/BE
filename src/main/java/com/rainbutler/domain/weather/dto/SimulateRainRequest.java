package com.rainbutler.domain.weather.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 시연용 "비 오는 중" 강제 요청입니다.
 *
 * @param minutes 지금부터 비 오는 중으로 표시할 시간 (분, 0~360). 0이면 해제
 */
public record SimulateRainRequest(
        @Schema(example = "60") @NotNull @Min(0) @Max(360) Integer minutes) {
}
