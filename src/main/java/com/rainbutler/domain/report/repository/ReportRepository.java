package com.rainbutler.domain.report.repository;

import com.rainbutler.domain.report.entity.Report;
import com.rainbutler.domain.report.entity.ReportStatus;
import com.rainbutler.domain.report.entity.ReportType;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Report 엔티티 저장소입니다.
 */
public interface ReportRepository extends JpaRepository<Report, Long> {

    /**
     * 빗물받이에 처리 대기 중인 특정 종류의 제보가 있는지 확인합니다. (표시 상태 계산)
     *
     * @param drainId 빗물받이 id
     * @param type 제보 종류
     * @param status 처리 상태
     * @return 존재 여부
     */
    boolean existsByDrain_IdAndTypeAndStatus(Long drainId, ReportType type, ReportStatus status);

    /**
     * 여러 빗물받이 중 처리 대기 중인 막힘 제보가 있는 빗물받이 id를 찾습니다. (지도 · 목록)
     *
     * @param drainIds 빗물받이 id 목록
     * @return 막힘 제보가 있는 빗물받이 id 목록
     */
    @Query("SELECT DISTINCT r.drain.id FROM Report r WHERE r.drain.id IN :drainIds "
            + "AND r.type = com.rainbutler.domain.report.entity.ReportType.BLOCKAGE "
            + "AND r.status = com.rainbutler.domain.report.entity.ReportStatus.PENDING")
    List<Long> findPendingBlockageDrainIds(@Param("drainIds") List<Long> drainIds);

    /**
     * 사용자별 제보 목록을 최신순으로 조회합니다.
     *
     * @param userId 사용자 id
     * @return 제보 목록
     */
    List<Report> findByUser_IdOrderByCreatedAtDesc(Long userId);

    /**
     * 내 제보 기록을 최신순으로 커서 이후부터 찾습니다. (활동 내역)
     *
     * @param userId 사용자 id
     * @param cursorTime 이 시각보다 이전 기록만 (첫 페이지는 미래 시각)
     * @param cursorId 같은 시각일 때 이 id보다 작은 기록만
     * @param sameType 커서와 같은 종류라서 시각 · id를 함께 비교할지
     * @param afterType 커서 종류보다 뒤라서 같은 시각을 모두 포함할지
     * @param pageable 가져올 개수
     * @return 제보 목록
     */
    @Query("SELECT r FROM Report r LEFT JOIN FETCH r.drain "
            + "WHERE r.user.id = :userId "
            + "AND (r.createdAt < :cursorTime "
            + "     OR (:sameType = true AND r.createdAt = :cursorTime AND r.id < :cursorId) "
            + "     OR (:afterType = true AND r.createdAt = :cursorTime)) "
            + "ORDER BY r.createdAt DESC, r.id DESC")
    List<Report> findPageByUserId(
            @Param("userId") Long userId,
            @Param("cursorTime") LocalDateTime cursorTime,
            @Param("cursorId") Long cursorId,
            @Param("sameType") boolean sameType,
            @Param("afterType") boolean afterType,
            Pageable pageable);

    /**
     * 전체 제보 목록을 최신순으로 조회합니다. (관리자용)
     *
     * @return 제보 목록
     */
    List<Report> findAllByOrderByCreatedAtDesc();

    /**
     * 처리 상태별 제보 목록을 최신순으로 조회합니다. (관리자용)
     *
     * @param status 처리 상태
     * @return 제보 목록
     */
    List<Report> findByStatusOrderByCreatedAtDesc(ReportStatus status);
}
