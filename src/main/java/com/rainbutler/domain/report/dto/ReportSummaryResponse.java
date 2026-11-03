package com.rainbutler.domain.report.dto;

import com.rainbutler.domain.report.entity.Report;
import com.rainbutler.domain.report.entity.ReportStatus;
import com.rainbutler.domain.report.entity.ReportType;
import java.time.LocalDateTime;

/**
 * 제보 목록의 한 건입니다.
 *
 * @param reportId 제보 id
 * @param drainId 빗물받이 id
 * @param type 제보 종류
 * @param status 처리 상태
 * @param createdAt 제보 생성 시각
 */
public record ReportSummaryResponse(
        Long reportId,
        Long drainId,
        ReportType type,
        ReportStatus status,
        LocalDateTime createdAt
) {

    /**
     * 제보 엔티티를 목록 요약으로 바꿉니다.
     *
     * @param report 제보
     * @return 제보 요약
     */
    public static ReportSummaryResponse from(Report report) {
        return new ReportSummaryResponse(
                report.getId(),
                report.getDrain().getId(),
                report.getType(),
                report.getStatus(),
                report.getCreatedAt());
    }
}
