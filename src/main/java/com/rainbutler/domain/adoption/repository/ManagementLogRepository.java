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
     * 같은 사용자가 같은 빗물받이를 하루에 이미 인정받았는지 셉니다. (경험치 하루 1회)
     *
     * @param userId 사용자 id
     * @param drainId 빗물받이 id
     * @param excludeId 지금 지급할 기록 id (자기 자신 제외)
     * @param from 하루 시작
     * @param to 다음 날 시작
     * @return 같은 날 인정된 다른 관리 기록 수
     */
    @Query("SELECT COUNT(m) FROM ManagementLog m WHERE m.adoption.user.id = :userId "
            + "AND m.adoption.drain.id = :drainId "
            + "AND m.id <> :excludeId "
            + "AND m.status = com.rainbutler.domain.adoption.entity.ManagementStatus.VERIFIED "
            + "AND m.managedAt >= :from AND m.managedAt < :to")
    long countVerifiedOnDay(
            @Param("userId") Long userId,
            @Param("drainId") Long drainId,
            @Param("excludeId") Long excludeId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
