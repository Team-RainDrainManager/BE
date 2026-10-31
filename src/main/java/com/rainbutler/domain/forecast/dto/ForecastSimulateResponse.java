package com.rainbutler.domain.forecast.dto;

import com.rainbutler.domain.forecast.service.ForecastApplyResult;

/**
 * 가상 예보 적용 결과입니다.
 *
 * @param forecastId 대상 가상 예보 id (IGNORED면 null)
 * @param applied CONFIRMED · UPDATED · CANCELLED · IGNORED
 * @param createdCount 이번에 생성된 점검 요청 수 (06:00 일괄 발송 대상이면 0)
 * @param cancelledCount 이번에 취소된 점검 요청 수
 */
public record ForecastSimulateResponse(
        Long forecastId, ForecastApplyResult.Applied applied, int createdCount,
        int cancelledCount) {

    /**
     * 반영 결과와 요청 수로 응답을 만듭니다.
     */
    public static ForecastSimulateResponse of(
            ForecastApplyResult result, int createdCount, int cancelledCount) {
        return new ForecastSimulateResponse(
                result.forecastId(), result.applied(), createdCount, cancelledCount);
    }
}
