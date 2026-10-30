package com.rainbutler.domain.forecast.client;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 하루치 비 예보 요약입니다.
 *
 * @param date 날짜
 * @param maxPop 강수확률 최댓값 (%)
 * @param totalMm 1시간 강수량 합계 (mm)
 * @param firstRainAt 비가 시작되는 첫 시각 (강수량 &gt; 0 이거나 강수확률 ≥ 기준, 없으면 null)
 */
public record DailyRain(
        LocalDate date, int maxPop, BigDecimal totalMm, LocalDateTime firstRainAt) {
}
