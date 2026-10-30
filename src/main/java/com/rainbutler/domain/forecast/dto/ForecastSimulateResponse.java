package com.rainbutler.domain.forecast.dto;

import com.rainbutler.domain.forecast.service.ForecastApplyResult;

/**
 * 가상 예보 적용 결과입니다.
 *
 * @param forecastId 대상 가상 예보 id (IGNORED면 null)
 * @param applied CONFIRMED · UPDATED · CANCELLED · IGNORED
 * @param createdCount 이번에 생성된 점검 요청 수 (요청 생성 기능 전까지 0)
 * @param cancelledCount 이번에 취소된 점검 요청 수 (요청 생성 기능 전까지 0)
 */
public record ForecastSimulateResponse(
        Long forecastId, ForecastApplyResult.Applied applied, int createdCount,
        int cancelledCount) {

    /**
     * 반영 결과로 응답을 만듭니다.
     */
    public static ForecastSimulateResponse from(ForecastApplyResult result) {
        return new ForecastSimulateResponse(result.forecastId(), result.applied(), 0, 0);
    }
}
