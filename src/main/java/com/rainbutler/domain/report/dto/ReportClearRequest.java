package com.rainbutler.domain.report.dto;

import jakarta.validation.constraints.Size;

/**
 * 승인된 제보의 입양 차단 해제 요청입니다.
 *
 * @param adminComment 해제 코멘트 (선택, 최대 500자)
 */
public record ReportClearRequest(
        @Size(max = 500) String adminComment
) {
}
