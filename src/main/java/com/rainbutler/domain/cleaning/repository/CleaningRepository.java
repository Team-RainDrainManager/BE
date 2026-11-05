package com.rainbutler.domain.cleaning.repository;

import com.rainbutler.domain.cleaning.entity.Cleaning;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Cleaning 엔티티 저장소입니다. 쿼리 메서드는 도메인 담당자가 추가합니다.
 */
public interface CleaningRepository extends JpaRepository<Cleaning, Long> {

    /**
     * 같은 사진으로 등록된 청소가 있는지 확인합니다.
     *
     * @param beforePhotoUrl 청소 전 사진 URL
     * @return 존재 여부
     */
    boolean existsByBeforePhotoUrl(String beforePhotoUrl);

    /**
     * 관리자 검토를 기다리는 청소를 최신순으로 찾습니다.
     *
     * <p>청소 인증을 통과한 기록만 검토 대상입니다. (진행 중 · 실패한 청소는 사진을 다시 내야 한다)
     *
     * @param pageable 가져올 개수
     * @return 검토 대기 청소
     */
    @Query("SELECT c FROM Cleaning c JOIN FETCH c.user JOIN FETCH c.drain "
            + "WHERE c.reviewStatus = com.rainbutler.global.entity.ReviewStatus.PENDING "
            + "AND c.status = com.rainbutler.domain.cleaning.entity.CleaningStatus.COMPLETED "
            + "ORDER BY c.createdAt DESC, c.id DESC")
    List<Cleaning> findPendingReviews(Pageable pageable);
}
