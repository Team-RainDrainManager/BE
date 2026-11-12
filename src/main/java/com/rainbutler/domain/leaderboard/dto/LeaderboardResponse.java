package com.rainbutler.domain.leaderboard.dto;

import com.rainbutler.domain.leaderboard.entity.LeaderboardType;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 리더보드 한 페이지입니다.
 *
 * @param type 집계 기간 (WEEKLY · TOTAL)
 * @param periodStart 집계 시작 시각 (TOTAL이면 null)
 * @param totalParticipants 순위에 든 전체 사용자 수 (음수 점수 제외)
 * @param rankings 상위 순위
 * @param me 로그인한 사용자의 순위 (순위권 밖이어도 내려간다)
 */
public record LeaderboardResponse(
        LeaderboardType type,
        LocalDateTime periodStart,
        int totalParticipants,
        List<LeaderboardRowResponse> rankings,
        LeaderboardRowResponse me
) {
}
