package com.rainbutler.domain.report.dto;

/**
 * 제보 생성 응답입니다.
 *
 * @param reportId 제보 id
 * @param blocksAdoption 이 제보가 빗물받이 입양을 막는지 (AI가 청소 불가로 판정한 막힘 제보)
 */
public record ReportCreateResponse(Long reportId, boolean blocksAdoption) {
}
