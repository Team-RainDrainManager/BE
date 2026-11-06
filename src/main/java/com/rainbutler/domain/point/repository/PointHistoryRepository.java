package com.rainbutler.domain.point.repository;

import com.rainbutler.domain.point.entity.PointHistory;
import com.rainbutler.domain.point.entity.PointSourceType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * PointHistory 엔티티 저장소입니다. 쿼리 메서드는 도메인 담당자가 추가합니다.
 */
public interface PointHistoryRepository extends JpaRepository<PointHistory, Long> {

    /**
     * 같은 활동에 이미 점수를 지급했는지 확인합니다. (중복 지급 방지)
     *
     * @param sourceType 점수 출처
     * @param sourceId 출처 기록 id
     * @return 존재 여부
     */
    boolean existsBySourceTypeAndSourceId(PointSourceType sourceType, Long sourceId);

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

    /**
     * 봉사 시간 산정에 쓰는 활동 점수입니다. 점검 · 청소 · 입양 관리 적립만 더하고 제보 · 회수는 뺍니다.
     *
     * @param userId 사용자 id
     * @param sources 활동 점수로 인정하는 출처
     * @return 활동 적립 점수 합
     */
    @Query("SELECT COALESCE(SUM(ABS(p.amount)), 0) FROM PointHistory p "
            + "WHERE p.user.id = :userId "
            + "AND p.type = com.rainbutler.domain.point.entity.PointType.EARN "
            + "AND p.sourceType IN :sources")
    long sumActivityPoints(
            @Param("userId") Long userId, @Param("sources") Collection<PointSourceType> sources);

    /**
     * 내 경험치 내역을 최신순으로 커서 이후부터 찾습니다. (활동 내역)
     *
     * @param userId 사용자 id
     * @param cursorTime 이 시각보다 이전 기록만 (첫 페이지는 미래 시각)
     * @param cursorId 같은 시각일 때 이 id보다 작은 기록만
     * @param sameType 커서와 같은 종류라서 시각 · id를 함께 비교할지
     * @param afterType 커서 종류보다 뒤라서 같은 시각을 모두 포함할지
     * @param pageable 가져올 개수
     * @return 경험치 내역
     */
    @Query("SELECT p FROM PointHistory p WHERE p.user.id = :userId "
            + "AND (p.createdAt < :cursorTime "
            + "     OR (:sameType = true AND p.createdAt = :cursorTime AND p.id < :cursorId) "
            + "     OR (:afterType = true AND p.createdAt = :cursorTime)) "
            + "ORDER BY p.createdAt DESC, p.id DESC")
    List<PointHistory> findPageByUserId(
            @Param("userId") Long userId,
            @Param("cursorTime") LocalDateTime cursorTime,
            @Param("cursorId") Long cursorId,
            @Param("sameType") boolean sameType,
            @Param("afterType") boolean afterType,
            Pageable pageable);
}
