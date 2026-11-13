package com.rainbutler.domain.inspection.repository;

import com.rainbutler.domain.inspection.entity.Inspection;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Inspection 엔티티 저장소입니다.
 */
public interface InspectionRepository extends JpaRepository<Inspection, Long> {

    /**
     * 같은 비 예보 · 대신 점검 요청으로 이미 점검을 만들었는지 확인합니다. (중복 제출 방지)
     *
     * @param requestId 점검 요청 id
     * @return 이미 있으면 true
     */
    boolean existsByRequest_Id(Long requestId);

    /**
     * 점검 전 사진 key가 이미 쓰였는지 확인합니다. (같은 사진 재사용 차단)
     *
     * @param photoKey 사진 key
     * @return 이미 있으면 true
     */
    boolean existsByBeforePhotoKey(String photoKey);

    /**
     * 점검 후 사진 key가 이미 쓰였는지 확인합니다. (같은 사진 재사용 차단)
     *
     * @param photoKey 사진 key
     * @return 이미 있으면 true
     */
    boolean existsByAfterPhotoKey(String photoKey);

    /**
     * 입양별 점검 기록을 최신순(id 내림차순)으로 커서 이후부터 찾습니다.
     *
     * <p>목록 응답에 빗물받이 · 입양 별명이 필요해 함께 가져옵니다.
     *
     * @param adoptionId 입양 id
     * @param cursor 이 id보다 작은 기록만 (첫 페이지는 Long.MAX_VALUE)
     * @param pageable 가져올 개수
     * @return 점검 기록 목록
     */
    @Query("SELECT i FROM Inspection i JOIN FETCH i.drain "
            + "LEFT JOIN FETCH i.adoption a LEFT JOIN FETCH a.user "
            + "WHERE i.adoption.id = :adoptionId AND i.id < :cursor ORDER BY i.id DESC")
    List<Inspection> findPageByAdoptionId(
            @Param("adoptionId") Long adoptionId,
            @Param("cursor") Long cursor,
            Pageable pageable);

    /**
     * 내 점검 기록을 최신순으로 커서 이후부터 찾습니다. (활동 내역)
     *
     * @param userId 사용자 id
     * @param cursorTime 이 시각보다 이전 기록만 (첫 페이지는 미래 시각)
     * @param cursorId 같은 시각일 때 이 id보다 작은 기록만
     * @param sameType 커서와 같은 종류라서 시각 · id를 함께 비교할지
     * @param afterType 커서 종류보다 뒤라서 같은 시각을 모두 포함할지
     * @param pageable 가져올 개수
     * @return 점검 기록 목록
     */
    @Query("SELECT i FROM Inspection i JOIN FETCH i.drain LEFT JOIN FETCH i.adoption "
            + "WHERE i.user.id = :userId "
            + "AND (i.createdAt < :cursorTime "
            + "     OR (:sameType = true AND i.createdAt = :cursorTime AND i.id < :cursorId) "
            + "     OR (:afterType = true AND i.createdAt = :cursorTime)) "
            + "ORDER BY i.createdAt DESC, i.id DESC")
    List<Inspection> findPageByUserId(
            @Param("userId") Long userId,
            @Param("cursorTime") LocalDateTime cursorTime,
            @Param("cursorId") Long cursorId,
            @Param("sameType") boolean sameType,
            @Param("afterType") boolean afterType,
            Pageable pageable);

    /**
     * 한 주기 안에서 경험치를 받은 점검(청소 불필요) 수를 셉니다. (리워드 10 · 5 판정용)
     *
     * <p>경험치를 받지 못한 점검(하루 1회 제한 등)은 세지 않습니다. 그래야 다음 점검이 그 주기의
     * 첫 점검 대우를 받습니다.
     *
     * @param userId 사용자 id
     * @param drainId 빗물받이 id
     * @param from 주기 시작 (포함)
     * @param to 주기 끝 (제외)
     * @return 경험치를 받은 점검 수
     */
    @Query("SELECT COUNT(i) FROM Inspection i WHERE i.user.id = :userId "
            + "AND i.drain.id = :drainId AND i.xpAwarded > 0 "
            + "AND i.beforeGrade <= 1 "
            + "AND i.createdAt >= :from AND i.createdAt < :to")
    long countVisitRewards(
            @Param("userId") Long userId,
            @Param("drainId") Long drainId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * 한 주기 안에서 경험치를 받은 청소 수를 셉니다. (리워드 30 · 10 · 20 판정용)
     *
     * @param userId 사용자 id
     * @param drainId 빗물받이 id
     * @param from 주기 시작 (포함)
     * @param to 주기 끝 (제외)
     * @return 경험치를 받은 청소 수
     */
    @Query("SELECT COUNT(i) FROM Inspection i WHERE i.user.id = :userId "
            + "AND i.drain.id = :drainId AND i.xpAwarded > 0 "
            + "AND i.beforeGrade >= 2 "
            + "AND i.createdAt >= :from AND i.createdAt < :to")
    long countCleaningRewards(
            @Param("userId") Long userId,
            @Param("drainId") Long drainId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * 관리자 검토를 기다리는 점검 기록을 최신순으로 찾습니다.
     *
     * @param pageable 가져올 개수
     * @return 검토 대기 점검 기록
     */
    @Query("SELECT i FROM Inspection i JOIN FETCH i.user JOIN FETCH i.drain "
            + "LEFT JOIN FETCH i.adoption "
            + "WHERE i.status = com.rainbutler.domain.inspection.entity.InspectionStatus.PENDING "
            + "ORDER BY i.createdAt DESC, i.id DESC")
    List<Inspection> findPendingReviews(Pageable pageable);
}
