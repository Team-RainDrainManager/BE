package com.rainbutler.domain.leaderboard.repository;

import com.rainbutler.domain.point.entity.PointHistory;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 리더보드 점수 집계 저장소입니다.
 *
 * <p>점수는 {@code point_history}의 적립(EARN) 합에서 회수(REVOKE) 합을 뺀 값입니다. 회수 금액이
 * 양수 · 음수 어느 쪽으로 저장돼도 같은 결과가 나오도록 절댓값으로 계산합니다.
 *
 * <p>탈퇴한 사용자와 관리자 · 시청 관계자는 집계에서 뺍니다. 점수가 0 이하인 사용자는 순위에서
 * 빠지지만, 내 점수를 보여 줄 수 있도록 집계 자체는 남깁니다.
 */
public interface LeaderboardRepository extends Repository<PointHistory, Long> {

    /**
     * 이번 주 점수 순위를 집계합니다.
     *
     * @param from 집계 시작 (포함)
     * @param to 집계 끝 (포함)
     * @return 사용자별 점수 (점수 내림차순)
     */
    @Query("""
            SELECT new com.rainbutler.domain.leaderboard.repository.LeaderboardEntry(
                p.user.id, p.user.nickname, p.user.profileImage,
                SUM(CASE WHEN p.type = com.rainbutler.domain.point.entity.PointType.EARN
                    THEN ABS(p.amount) ELSE -ABS(p.amount) END))
            FROM PointHistory p
            WHERE p.createdAt >= :from AND p.createdAt <= :to
                AND p.user.role = com.rainbutler.domain.user.entity.Role.USER
            GROUP BY p.user.id, p.user.nickname, p.user.profileImage
            ORDER BY SUM(CASE WHEN p.type = com.rainbutler.domain.point.entity.PointType.EARN
                THEN ABS(p.amount) ELSE -ABS(p.amount) END) DESC, p.user.id ASC
            """)
    List<LeaderboardEntry> findWeeklyScores(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * 전체 누적 점수 순위를 집계합니다.
     *
     * @return 사용자별 점수 (점수 내림차순)
     */
    @Query("""
            SELECT new com.rainbutler.domain.leaderboard.repository.LeaderboardEntry(
                p.user.id, p.user.nickname, p.user.profileImage,
                SUM(CASE WHEN p.type = com.rainbutler.domain.point.entity.PointType.EARN
                    THEN ABS(p.amount) ELSE -ABS(p.amount) END))
            FROM PointHistory p
            WHERE p.user.role = com.rainbutler.domain.user.entity.Role.USER
            GROUP BY p.user.id, p.user.nickname, p.user.profileImage
            ORDER BY SUM(CASE WHEN p.type = com.rainbutler.domain.point.entity.PointType.EARN
                THEN ABS(p.amount) ELSE -ABS(p.amount) END) DESC, p.user.id ASC
            """)
    List<LeaderboardEntry> findTotalScores();
}
