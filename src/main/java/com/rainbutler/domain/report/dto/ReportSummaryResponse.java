package com.rainbutler.domain.report.dto;

import com.rainbutler.domain.report.entity.Report;
import com.rainbutler.domain.report.entity.ReportStatus;
import com.rainbutler.domain.report.entity.ReportType;
import com.rainbutler.domain.sanction.entity.Sanction;
import java.time.LocalDateTime;

/**
 * 제보 목록의 한 건입니다.
 *
 * @param reportId 제보 id
 * @param drainId 빗물받이 id
 * @param type 제보 종류
 * @param status 처리 상태
 * @param blocksAdoption 이 제보가 빗물받이 입양을 막는지
 * @param createdAt 제보 생성 시각
 * @param sanction 이 제보로 부과된 제재 (허위 제보 반려. 없으면 null)
 */
public record ReportSummaryResponse(
        Long reportId,
        Long drainId,
        ReportType type,
        ReportStatus status,
        boolean blocksAdoption,
        LocalDateTime createdAt,
        ReportSanctionResponse sanction
) {

    /**
     * 제보 엔티티를 목록 요약으로 바꿉니다.
     *
     * @param report 제보
     * @param sanction 이 제보로 부과된 제재 (없으면 null)
     * @return 제보 요약
     */
    public static ReportSummaryResponse from(Report report, Sanction sanction) {
        return new ReportSummaryResponse(
                report.getId(),
                report.getDrain() == null ? null : report.getDrain().getId(),
                report.getType(),
                report.getStatus(),
                report.isBlocksAdoption(),
                report.getCreatedAt(),
                ReportSanctionResponse.from(sanction));
    }
}
