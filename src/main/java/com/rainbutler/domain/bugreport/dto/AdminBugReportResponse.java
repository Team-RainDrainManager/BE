package com.rainbutler.domain.bugreport.dto;

import com.rainbutler.domain.bugreport.entity.BugReport;
import com.rainbutler.domain.bugreport.entity.BugReportCategory;
import com.rainbutler.domain.bugreport.entity.BugReportStatus;
import java.time.LocalDateTime;

/**
 * 관리자 버그 신고 목록의 한 건입니다.
 *
 * @param bugReportId 신고 id
 * @param category 분류
 * @param content 내용 미리보기 (앞 100자)
 * @param status 처리 상태
 * @param reporterNickname 신고자 닉네임 (탈퇴했으면 "탈퇴한 사용자")
 * @param createdAt 신고 시각
 */
public record AdminBugReportResponse(
        Long bugReportId,
        BugReportCategory category,
        String content,
        BugReportStatus status,
        String reporterNickname,
        LocalDateTime createdAt) {

    /**
     * 신고와 신고자 닉네임으로 응답을 만듭니다.
     */
    public static AdminBugReportResponse of(BugReport report, String reporterNickname) {
        return new AdminBugReportResponse(report.getId(), report.getCategory(),
                BugReportPreview.of(report.getContent()), report.getStatus(), reporterNickname,
                report.getCreatedAt());
    }
}
