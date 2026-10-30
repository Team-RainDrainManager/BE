package com.rainbutler.domain.forecast.event;

/**
 * 비 예보가 새로 확정됐습니다. 받는 쪽은 이 예보로 점검 요청을 만들고 보냅니다.
 *
 * <p>커밋 뒤에 받으려면 {@code @TransactionalEventListener(phase = AFTER_COMMIT)}을 씁니다.
 *
 * @param forecastId 확정된 예보 id
 * @param simulated 시연용 가상 예보 여부
 */
public record ForecastConfirmedEvent(Long forecastId, boolean simulated) {
}
