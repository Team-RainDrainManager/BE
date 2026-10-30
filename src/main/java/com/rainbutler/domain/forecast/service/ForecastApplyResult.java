package com.rainbutler.domain.forecast.service;

/**
 * 예보 값을 반영한 결과입니다.
 *
 * @param applied 결과 종류
 * @param forecastId 대상 예보 id (IGNORED면 null)
 */
public record ForecastApplyResult(Applied applied, Long forecastId) {

    /** 반영 결과 종류입니다. */
    public enum Applied {
        /** 기준을 넘어 새 예보를 확정했다 */
        CONFIRMED,
        /** 진행 중 예보의 값을 갱신했다 */
        UPDATED,
        /** 기준 미달로 진행 중 예보를 취소했다 */
        CANCELLED,
        /** 기준 미달이고 진행 중 예보도 없어 아무것도 하지 않았다 */
        IGNORED
    }
}
