package com.rainbutler.domain.point.repository;

import com.rainbutler.domain.point.entity.PointHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * PointHistory 엔티티 저장소입니다. 쿼리 메서드는 도메인 담당자가 추가합니다.
 */
public interface PointHistoryRepository extends JpaRepository<PointHistory, Long> {

    /**
     * 참여 점수: 적립(EARN) 합 − 회수(REVOKE) 합입니다. 이력이 없으면 0입니다.
     *
     * <p>회수 금액을 양수로 적든 음수로 적든 같은 결과가 나오도록 절댓값으로 더하고 뺍니다.
     *
     * @param userId 사용자 id
     * @return 참여 점수
     */
    @Query("SELECT COALESCE(SUM(CASE "
            + "WHEN p.type = com.rainbutler.domain.point.entity.PointType.EARN "
            + "THEN ABS(p.amount) ELSE -ABS(p.amount) END), 0) "
            + "FROM PointHistory p WHERE p.user.id = :userId")
    long sumParticipationScore(@Param("userId") Long userId);
}
