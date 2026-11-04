package com.rainbutler.domain.leaderboard.dto;

import com.rainbutler.domain.leaderboard.entity.LeaderboardType;
import java.time.LocalDateTime;

/**
 * 내 리더보드 순위입니다.
 *
 * @param type 집계 기간 (WEEKLY · TOTAL)
 * @param periodStart 집계 시작 시각 (TOTAL이면 null)
 * @param totalParticipants 순위에 든 전체 사용자 수 (점수 0 이하 제외)
 * @param rank 내 순위 (점수가 0 이하이면 null)
 * @param score 내 참여 점수 (음수일 수 있다)
 */
public record MyRankResponse(
        LeaderboardType type,
        LocalDateTime periodStart,
        int totalParticipants,
        Integer rank,
        long score
) {
}
