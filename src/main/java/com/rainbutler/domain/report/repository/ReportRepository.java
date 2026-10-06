package com.rainbutler.domain.report.repository;

import com.rainbutler.domain.report.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Report 엔티티 저장소입니다. 쿼리 메서드는 도메인 담당자가 추가합니다.
 */
public interface ReportRepository extends JpaRepository<Report, Long> {
}
