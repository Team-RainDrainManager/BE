package com.rainbutler.domain.inspection.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 청소 후 사진 인증 요청입니다.
 *
 * @param afterKey 청소 후 사진 key
 * @param safetyConfirmed 현장 안전 수칙 확인 여부 (필수)
 */
public record InspectionCompleteRequest(
        @NotBlank String afterKey,
        @NotNull Boolean safetyConfirmed
) {
}
