package com.rainbutler.domain.bugreport.dto;

import com.rainbutler.domain.bugreport.entity.BugReport;
import com.rainbutler.domain.bugreport.entity.BugReportStatus;
import java.time.LocalDateTime;

/**
 * 버그 신고 등록 결과입니다.
 *
 * @param bugReportId 신고 id
 * @param status 처리 상태 (RECEIVED)
 * @param createdAt 신고 시각
 */
public record BugReportCreateResponse(
        Long bugReportId, BugReportStatus status, LocalDateTime createdAt) {

    /**
     * 신고로 응답을 만듭니다.
     */
    public static BugReportCreateResponse from(BugReport report) {
        return new BugReportCreateResponse(
                report.getId(), report.getStatus(), report.getCreatedAt());
    }
}
