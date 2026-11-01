package com.rainbutler.domain.adoption.repository;

import com.rainbutler.domain.adoption.entity.ManagementLog;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
