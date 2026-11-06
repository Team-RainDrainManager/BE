package com.rainbutler.domain.request.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 지도 비 예보 배너입니다. 상태 · 값 · 기본 문구만 주고, 모양 · 문구 위치는 프론트가 정합니다.
 *
 * @param state 배너 상태 (RAINING 비 오는 중 · FORECAST 오늘 · 내일 비 예보 · NONE 배너 없음)
 * @param raining 지금 비가 오는지
 * @param observedAt 비 여부를 판단한 실황 기준 시각 (모르면 null)
 * @param forecast 이번 비 예보 (없으면 null)
 * @param help 이번 비 대신 점검 진행 상황 (예보가 없으면 null)
 * @param message 기본 문구 (NONE이면 null)
 */
public record RainNoticeResponse(
        State state,
        boolean raining,
        LocalDateTime observedAt,
        ForecastSummary forecast,
        HelpSummary help,
        String message) {

    /** 배너 상태입니다. (우선순위 RAINING > FORECAST > NONE) */
    public enum State {
        /** 지금 비가 오는 중 */
        RAINING,
        /** 오늘 · 내일 비 예보가 있음 */
        FORECAST,
        /** 배너 없음 */
        NONE
    }

    /**
     * 이번 비 예보입니다.
     *
     * @param forecastId 예보 id
     * @param rainDate 비 오는 날
     * @param rainStartAt 비 시작 예상 시각
     * @param completeDeadlineAt 진행 중 요청 완료 마감 중 가장 이른 값 (요청이 없으면 null)
     * @param simulated 시연용 가상 예보인지
     */
    public record ForecastSummary(Long forecastId, LocalDate rainDate, LocalDateTime rainStartAt,
            LocalDateTime completeDeadlineAt, boolean simulated) {
    }

    /**
     * 이번 비 대신 점검 진행 상황입니다.
     *
     * @param needsHelpCount 대신 점검이 필요한 빗물받이 (OPEN)
     * @param inProgressCount 누군가 점검하기로 한 빗물받이 (SENT · ACCEPTED · CLAIMED)
     * @param doneCount 점검을 마친 빗물받이 (COMPLETED)
     */
    public record HelpSummary(long needsHelpCount, long inProgressCount, long doneCount) {
    }
}
