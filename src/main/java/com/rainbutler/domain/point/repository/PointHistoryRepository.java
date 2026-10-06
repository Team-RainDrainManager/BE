package com.rainbutler.domain.point.repository;

import com.rainbutler.domain.point.entity.PointHistory;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * PointHistory 엔티티 저장소입니다. 쿼리 메서드는 도메인 담당자가 추가합니다.
 */
public interface PointHistoryRepository extends JpaRepository<PointHistory, Long> {
}
