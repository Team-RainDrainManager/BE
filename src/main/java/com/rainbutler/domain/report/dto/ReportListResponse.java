package com.rainbutler.domain.report.dto;

import java.util.List;

/**
 * 제보 목록 응답입니다.
 *
 * @param reports 제보 목록
 */
public record ReportListResponse(List<ReportSummaryResponse> reports) {
}
