package com.rainbutler.domain.user.dto;

import com.rainbutler.domain.terms.dto.TermsResponse;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 온보딩 상태입니다.
 *
 * @param completed 온보딩 완료 여부
 * @param completedAt 완료 시각 (미완료면 null)
 * @param identityVerified 본인인증 여부
 * @param pendingRequiredTerms 아직 동의하지 않은(또는 새 버전이 나온) 필수 약관 목록
 */
public record OnboardingStatusResponse(
        boolean completed,
        LocalDateTime completedAt,
        boolean identityVerified,
        List<TermsResponse> pendingRequiredTerms) {
}
