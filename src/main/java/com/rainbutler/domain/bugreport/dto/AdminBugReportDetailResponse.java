package com.rainbutler.domain.bugreport.dto;

import com.rainbutler.domain.bugreport.entity.BugReport;
import com.rainbutler.domain.bugreport.entity.BugReportCategory;
import com.rainbutler.domain.bugreport.entity.BugReportPlatform;
import com.rainbutler.domain.bugreport.entity.BugReportStatus;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 버그 신고 상세입니다.
 *
 * @param bugReportId 신고 id
 * @param category 분류
 * @param title 제목
 * @param content 내용 전체
 * @param screen 버그가 난 화면 · 경로
 * @param requestId 신고자가 마지막으로 받은 X-Request-Id (로그 검색용)
 * @param appVersion 앱 버전
 * @param platform WEB · IOS · ANDROID
 * @param userAgent 요청 User-Agent
 * @param photoUrls 스크린샷 조회 URL
 * @param status 처리 상태
 * @param adminNote 관리자 메모
 * @param reporterNickname 신고자 닉네임 (탈퇴했으면 "탈퇴한 사용자")
 * @param resolvedByNickname 해결 · 보류 처리한 관리자 닉네임
 * @param resolvedAt 해결 · 보류 처리 시각
 * @param createdAt 신고 시각
 */
public record AdminBugReportDetailResponse(
        Long bugReportId,
        BugReportCategory category,
        String title,
        String content,
        String screen,
        String requestId,
        String appVersion,
        BugReportPlatform platform,
        String userAgent,
        List<String> photoUrls,
        BugReportStatus status,
        String adminNote,
        String reporterNickname,
        String resolvedByNickname,
        LocalDateTime resolvedAt,
        LocalDateTime createdAt) {

    /**
     * 신고와 조회해 둔 값으로 응답을 만듭니다.
     */
    public static AdminBugReportDetailResponse of(BugReport report, List<String> photoUrls,
            String reporterNickname, String resolvedByNickname) {
        return new AdminBugReportDetailResponse(report.getId(), report.getCategory(),
                report.getTitle(), report.getContent(), report.getScreen(), report.getRequestId(),
                report.getAppVersion(), report.getPlatform(), report.getUserAgent(), photoUrls,
                report.getStatus(), report.getAdminNote(), reporterNickname, resolvedByNickname,
                report.getResolvedAt(), report.getCreatedAt());
    }
}
