package com.rainbutler.domain.adoption.repository;

import com.rainbutler.domain.adoption.entity.ManagementLog;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * ManagementLog 엔티티 저장소입니다. 쿼리 메서드는 도메인 담당자가 추가합니다.
 */
public interface ManagementLogRepository extends JpaRepository<ManagementLog, Long> {

    /**
     * 같은 비 예보 요청으로 이미 관리 기록을 남겼는지 확인합니다.
     *
     * @param requestId 비 예보 점검 요청 id
     * @return 존재 여부
     */
    boolean existsByRequest_Id(Long requestId);

    /**
     * 같은 점검 전 사진으로 이미 관리 기록을 남겼는지 확인합니다.
     *
     * @param beforePhotoUrl 점검 전 사진 key
     * @return 존재 여부
     */
    boolean existsByBeforePhotoUrl(String beforePhotoUrl);

    /**
     * 같은 청소 후 사진으로 이미 관리 기록을 남겼는지 확인합니다.
     *
     * @param afterPhotoUrl 청소 후 사진 key
     * @return 존재 여부
     */
    boolean existsByAfterPhotoUrl(String afterPhotoUrl);

    /**
     * 관리자 검토를 기다리는 관리 기록을 최신순으로 찾습니다.
     *
     * @param pageable 가져올 개수
     * @return 검토 대기 관리 기록
     */
    @Query("SELECT m FROM ManagementLog m JOIN FETCH m.adoption a "
            + "JOIN FETCH a.user JOIN FETCH a.drain "
            + "WHERE m.reviewStatus = com.rainbutler.global.entity.ReviewStatus.PENDING "
            + "ORDER BY m.createdAt DESC, m.id DESC")
    List<ManagementLog> findPendingReviews(Pageable pageable);

    /**
     * 한 주기 안에서 경험치를 받은 관리 점검 수를 셉니다. (리워드 10 · 5 판정용)
     *
     * <p>경험치 이력이 남은 기록만 셉니다. 하루 1회 제한으로 0점이 된 기록은 다음 점검이 첫 점검
     * 대우를 받도록 세지 않습니다.
     *
     * @param userId 사용자 id
     * @param drainId 빗물받이 id
     * @param from 주기 시작 (포함)
     * @param to 주기 끝 (제외)
     * @return 경험치를 받은 점검 수
     */
    @Query("SELECT COUNT(m) FROM ManagementLog m WHERE m.adoption.user.id = :userId "
            + "AND m.adoption.drain.id = :drainId "
            + "AND m.actionType = com.rainbutler.domain.adoption.entity.ManagementActionType.INSPECTION "
            + "AND m.status = com.rainbutler.domain.adoption.entity.ManagementStatus.VERIFIED "
            + "AND m.managedAt >= :from AND m.managedAt < :to "
            + "AND EXISTS (SELECT p.id FROM PointHistory p "
            + "     WHERE p.sourceType = com.rainbutler.domain.point.entity.PointSourceType.MANAGEMENT "
            + "     AND p.sourceId = m.id)")
    long countVisitRewards(
            @Param("userId") Long userId,
            @Param("drainId") Long drainId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * 한 주기 안에서 경험치를 받은 관리 청소 수를 셉니다. (리워드 30 · 10 · 20 판정용)
     *
     * @param userId 사용자 id
     * @param drainId 빗물받이 id
     * @param from 주기 시작 (포함)
     * @param to 주기 끝 (제외)
     * @return 경험치를 받은 청소 수
     */
    @Query("SELECT COUNT(m) FROM ManagementLog m WHERE m.adoption.user.id = :userId "
            + "AND m.adoption.drain.id = :drainId "
            + "AND m.actionType = com.rainbutler.domain.adoption.entity.ManagementActionType.CLEANING "
            + "AND m.status = com.rainbutler.domain.adoption.entity.ManagementStatus.VERIFIED "
            + "AND m.managedAt >= :from AND m.managedAt < :to "
            + "AND EXISTS (SELECT p.id FROM PointHistory p "
            + "     WHERE p.sourceType = com.rainbutler.domain.point.entity.PointSourceType.MANAGEMENT "
            + "     AND p.sourceId = m.id)")
    long countCleaningRewards(
            @Param("userId") Long userId,
            @Param("drainId") Long drainId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
