package com.rainbutler.domain.forecast.event;

import java.time.LocalDateTime;

/**
 * 진행 중 예보의 비 시작 시각이 앞당겨졌습니다. 받는 쪽은 진행 중 요청의 마감을 당깁니다.
 *
 * <p>늦춰진 경우에는 발행하지 않습니다 (이미 알린 마감을 늦추지 않는다).
 *
 * @param forecastId 예보 id
 * @param previousRainStartAt 이전 비 시작 시각
 * @param rainStartAt 새 비 시작 시각
 */
public record ForecastRainStartAdvancedEvent(
        Long forecastId, LocalDateTime previousRainStartAt, LocalDateTime rainStartAt) {
}
