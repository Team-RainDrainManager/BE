package com.rainbutler.domain.leaderboard.repository;

/**
 * 리더보드 집계 쿼리의 한 행입니다.
 *
 * @param userId 사용자 id
 * @param nickname 닉네임
 * @param profileImage 프로필 이미지 URL (없으면 null)
 * @param score 집계 점수 (적립 − 회수)
 */
public record LeaderboardEntry(
        Long userId,
        String nickname,
        String profileImage,
        Long score
) {
}
