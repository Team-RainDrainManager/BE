package com.rainbutler.domain.report.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 제보 승인/반려 요청입니다.
 *
 * @param approved 승인 여부 (true: 승인, false: 반려)
 * @param adminComment 관리자 코멘트 (반려 시 필수, 최대 500자)
 */
public record ReportReviewRequest(
        @NotNull Boolean approved,
        @Size(max = 500) String adminComment
) {
}
