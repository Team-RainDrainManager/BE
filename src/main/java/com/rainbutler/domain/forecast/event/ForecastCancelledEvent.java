package com.rainbutler.domain.forecast.event;

/**
 * 진행 중 예보가 기준 미달로 취소됐습니다. 받는 쪽은 진행 중 요청을 취소하고 취소 알림을 보냅니다.
 *
 * @param forecastId 취소된 예보 id
 */
public record ForecastCancelledEvent(Long forecastId) {
}
