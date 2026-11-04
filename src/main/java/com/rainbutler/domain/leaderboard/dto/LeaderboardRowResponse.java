package com.rainbutler.domain.leaderboard.dto;

/**
 * 리더보드 순위 한 줄입니다.
 *
 * @param rank 순위 (동점이면 같은 순위, 점수가 0 이하이면 null)
 * @param userId 사용자 id
 * @param nickname 닉네임
 * @param profileImage 프로필 이미지 URL (없으면 null)
 * @param score 참여 점수
 * @param isMe 로그인한 사용자 본인 여부
 */
public record LeaderboardRowResponse(
        Integer rank,
        Long userId,
        String nickname,
        String profileImage,
        long score,
        boolean isMe
) {
}
