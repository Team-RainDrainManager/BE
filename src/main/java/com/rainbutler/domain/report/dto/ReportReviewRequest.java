package com.rainbutler.domain.report.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 제보 승인/반려 요청입니다.
 *
 * @param approved 승인 여부 (true: 승인, false: 반려)
 * @param adminComment 관리자 코멘트 (반려 · 제재 시 필수, 최대 500자). 제재면 제재 사유로 사용자에게 보인다
 * @param sanction 허위 제보로 보고 반려와 함께 제보자를 제재할지 (null · false면 제재 없음, ADMIN만)
 */
public record ReportReviewRequest(
        @NotNull Boolean approved,
        @Size(max = 500) String adminComment,
        Boolean sanction
) {

    /**
     * 제재를 요청했는지 확인합니다.
     *
     * @return sanction이 true면 true
     */
    public boolean isSanction() {
        return Boolean.TRUE.equals(sanction);
    }
}
