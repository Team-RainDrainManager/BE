package com.rainbutler.domain.forecast.dto;

/**
 * 가상 예보 일괄 취소 결과입니다.
 *
 * @param cancelledForecastCount 취소한 가상 예보 수
 * @param cancelledRequestCount 함께 취소된 점검 요청 수 (취소 알림이 간 요청)
 */
public record SimulatedForecastCancelResponse(
        int cancelledForecastCount, int cancelledRequestCount) {
}
