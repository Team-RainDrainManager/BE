package com.rainbutler.domain.review.dto;

import com.rainbutler.domain.review.entity.ReviewDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 관리자 사진 검토 요청입니다.
 *
 * @param decision 승인(APPROVE) · 반려(REJECT)
 * @param reason 반려 사유 (반려 시 필수, 최대 500자)
 */
public record ReviewDecisionRequest(
        @NotNull ReviewDecision decision,
        @Size(max = 500) String reason
) {
}
