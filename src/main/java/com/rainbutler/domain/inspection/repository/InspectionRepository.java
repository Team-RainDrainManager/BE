package com.rainbutler.domain.inspection.repository;

import com.rainbutler.domain.inspection.entity.Inspection;
import java.time.LocalDateTime;
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
     * 같은 사용자가 같은 빗물받이에서 기간 안에 경험치를 받은 점검 수를 셉니다. (빗물받이별 하루 1회)
     *
     * @param userId 사용자 id
     * @param drainId 빗물받이 id
     * @param from 시작 (포함)
     * @param to 끝 (제외)
     * @return 경험치를 받은 점검 수
     */
    @Query("SELECT COUNT(i) FROM Inspection i WHERE i.user.id = :userId "
            + "AND i.drain.id = :drainId AND i.xpAwarded > 0 "
            + "AND i.createdAt >= :from AND i.createdAt < :to")
    long countRewardedBetween(
            @Param("userId") Long userId,
            @Param("drainId") Long drainId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
