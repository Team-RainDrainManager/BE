package com.rainbutler.domain.review.dto;

import com.rainbutler.domain.review.entity.ReviewTargetType;
import com.rainbutler.global.entity.ReviewStatus;
import java.time.LocalDateTime;

/**
 * 관리자 사진 검토 결과입니다.
 *
 * @param type 검토 대상 종류
 * @param id 검토 대상 id
 * @param reviewStatus 처리 후 검토 상태 (APPROVED · REJECTED)
 * @param reviewedAt 처리 시각
 * @param xpAwarded 이번 승인으로 지급한 경험치 (승인 · 점검만, 나머지는 0)
 */
public record ReviewDecisionResponse(
        ReviewTargetType type,
        Long id,
        ReviewStatus reviewStatus,
        LocalDateTime reviewedAt,
        int xpAwarded
) {
}
