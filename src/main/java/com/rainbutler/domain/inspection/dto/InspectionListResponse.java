package com.rainbutler.domain.inspection.dto;

import java.util.List;

/**
 * 입양별 점검 기록 한 페이지입니다.
 *
 * @param nextCursor 다음 페이지 요청에 보낼 cursor (다음 페이지가 없으면 null)
 * @param inspections 최신순 점검 기록
 */
public record InspectionListResponse(Long nextCursor, List<InspectionSummaryResponse> inspections) {
}
