package com.rainbutler.domain.adoption.repository;

import com.rainbutler.domain.adoption.entity.ManagementLog;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * ManagementLog 엔티티 저장소입니다. 쿼리 메서드는 도메인 담당자가 추가합니다.
 */
public interface ManagementLogRepository extends JpaRepository<ManagementLog, Long> {
}
