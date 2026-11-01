package com.rainbutler.domain.adoption.dto.Response;

import java.time.LocalDateTime;

/**
 * 로그인 사용자가 현재 입양 중인 빗물받이 한 건의 요약입니다.
 *
 * @param adoptionId 입양 id
 * @param nickname 빗물받이 별명
 * @param drainId 빗물받이 id
 * @param level 캐릭터 레벨
 * @param score 누적 성장 점수
 * @param lastManagedAt 마지막 돌봄 시각 (입양일 · 마지막 승인 점검 중 늦은 시각)
 * @param nextManagementAt 다음 관리 예정 시각
 * @param releaseWarning 28일 이상 돌봄 기록이 없어 자동 해제 전 경고가 필요한지
 * @param missedForecastCount 연속 비 예보 요청 무응답 횟수
 * @param lonely 외로운 또랑이 표시 대상인지
 */
public record MyAdoptionResponse(
        Long adoptionId,
        String nickname,
        Long drainId,
        Integer level,
        Integer score,
        LocalDateTime lastManagedAt,
        LocalDateTime nextManagementAt,
        boolean releaseWarning,
        Integer missedForecastCount,
        boolean lonely
) {
}
