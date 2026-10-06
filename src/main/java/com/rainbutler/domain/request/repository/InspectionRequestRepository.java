package com.rainbutler.domain.request.repository;

import com.rainbutler.domain.request.entity.InspectionRequest;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * InspectionRequest 엔티티 저장소입니다. 쿼리 메서드는 도메인 담당자가 추가합니다.
 */
public interface InspectionRequestRepository extends JpaRepository<InspectionRequest, Long> {
}
