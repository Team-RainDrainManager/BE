package com.rainbutler.domain.forecast.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 가상 예보 적용 요청입니다. (시연용)
 *
 * @param pop 강수확률 최댓값 (0~100 %)
 * @param mm 강수량 합계 (0~500 mm)
 * @param rainStartAt 비 시작 시각 (지금부터 3시간 뒤 이후). 이 날짜의 가상 예보로 반영한다
 */
public record ForecastSimulateRequest(
        @Schema(example = "80") @NotNull @Min(0) @Max(100) Integer pop,
        @Schema(example = "12.5") @NotNull @DecimalMin("0") @DecimalMax("500") BigDecimal mm,
        @Schema(example = "2026-11-02T15:00:00") @NotNull LocalDateTime rainStartAt) {
}
