package com.rainbutler.domain.request.repository;

import com.rainbutler.domain.request.entity.InspectionRequest;
import com.rainbutler.domain.request.entity.RequestStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * InspectionRequest 엔티티 저장소입니다. 쿼리 메서드는 도메인 담당자가 추가합니다.
 */
public interface InspectionRequestRepository extends JpaRepository<InspectionRequest, Long> {

    /**
     * 입양들에 걸린 요청 중 해당 상태인 것을 찾습니다. (입양 해제 시 재공개할 SENT · ACCEPTED 요청)
     *
     * @param adoptionIds 입양 id 목록
     * @param statuses 요청 상태
     * @return 요청 목록
     */
    List<InspectionRequest> findAllByAdoption_IdInAndStatusIn(
            Collection<Long> adoptionIds, Collection<RequestStatus> statuses);

    /**
     * 사용자가 대신 점검을 맡은 요청 중 해당 상태인 것을 찾습니다. (탈퇴 시 재공개할 CLAIMED 요청)
     *
     * @param userId 대신 점검자 id
     * @param status 요청 상태
     * @return 요청 목록
     */
    List<InspectionRequest> findAllBySubstituteUser_IdAndStatus(Long userId, RequestStatus status);
}
