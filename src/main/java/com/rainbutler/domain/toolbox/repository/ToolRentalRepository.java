package com.rainbutler.domain.toolbox.repository;

import com.rainbutler.domain.toolbox.entity.ToolRental;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * ToolRental 엔티티 저장소입니다. 쿼리 메서드는 도메인 담당자가 추가합니다.
 */
public interface ToolRentalRepository extends JpaRepository<ToolRental, Long> {
}
