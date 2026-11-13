package com.rainbutler.domain.bugreport.dto;

import com.rainbutler.domain.bugreport.entity.BugReport;
import com.rainbutler.domain.bugreport.entity.BugReportCategory;
import com.rainbutler.domain.bugreport.entity.BugReportStatus;
import java.time.LocalDateTime;

/**
 * 내 신고 목록의 한 건입니다. 관리자 메모는 내려주지 않습니다.
 *
 * @param bugReportId 신고 id
 * @param category 분류
 * @param content 내용 미리보기 (앞 100자)
 * @param status 처리 상태 (RECEIVED 접수 · IN_PROGRESS 확인 중 · RESOLVED 해결 · WONT_FIX 보류)
 * @param createdAt 신고 시각
 * @param resolvedAt 해결 · 보류 처리 시각 (아니면 null)
 */
public record MyBugReportResponse(
        Long bugReportId,
        BugReportCategory category,
        String content,
        BugReportStatus status,
        LocalDateTime createdAt,
        LocalDateTime resolvedAt) {

    /**
     * 신고로 응답을 만듭니다.
     */
    public static MyBugReportResponse from(BugReport report) {
        return new MyBugReportResponse(report.getId(), report.getCategory(),
                BugReportPreview.of(report.getContent()), report.getStatus(),
                report.getCreatedAt(), report.getResolvedAt());
    }
}
