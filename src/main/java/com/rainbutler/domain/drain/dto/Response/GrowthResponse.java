package com.rainbutler.domain.drain.dto.Response;

/**
 * 성장 조회 응답입니다. 성장은 입양에 귀속되며 집사가 바뀌면 초기화됩니다.
 *
 * @param level 현재 레벨 (1 ~ 10, 만렙 10)
 * @param score 현재 누적 점수
 * @param nextLevelScore 다음 레벨이 되는 누적 점수 (만렙이면 현재 점수)
 * @param progressPercent 현재 레벨 구간 진행률 (0 ~ 100, 만렙이면 100)
 */
public record GrowthResponse(
        int level,
        int score,
        int nextLevelScore,
        int progressPercent
) {
}
