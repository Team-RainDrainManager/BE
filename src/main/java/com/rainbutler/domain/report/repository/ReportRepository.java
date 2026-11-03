package com.rainbutler.domain.report.repository;

import com.rainbutler.domain.report.entity.Report;
import com.rainbutler.domain.report.entity.ReportStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Report 엔티티 저장소입니다.
 */
public interface ReportRepository extends JpaRepository<Report, Long> {

    /**
     * 사용자별 제보 목록을 최신순으로 조회합니다.
     *
     * @param userId 사용자 id
     * @return 제보 목록
     */
    List<Report> findByUser_IdOrderByCreatedAtDesc(Long userId);

    /**
     * 전체 제보 목록을 최신순으로 조회합니다. (관리자용)
     *
     * @return 제보 목록
     */
    List<Report> findAllByOrderByCreatedAtDesc();

    /**
     * 처리 상태별 제보 목록을 최신순으로 조회합니다. (관리자용)
     *
     * @param status 처리 상태
     * @return 제보 목록
     */
    List<Report> findByStatusOrderByCreatedAtDesc(ReportStatus status);
}
