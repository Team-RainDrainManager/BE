package com.rainbutler.domain.weather.dto;

import java.time.LocalDateTime;

/**
 * 시연용 "비 오는 중" 강제 결과입니다.
 *
 * @param raining 지금 비 오는 중으로 판단되는지 (강제 해제 뒤에는 실제 실황 기준)
 * @param simulatedRainingUntil 강제 비 종료 시각 (해제했으면 null)
 */
public record SimulateRainResponse(boolean raining, LocalDateTime simulatedRainingUntil) {
}
