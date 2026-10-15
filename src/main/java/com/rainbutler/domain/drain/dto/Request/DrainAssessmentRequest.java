package com.rainbutler.domain.drain.dto.Request;

import jakarta.validation.constraints.NotBlank;

/**
 * 빗물받이 사진 단독 판독 요청입니다. (생성 없이 판정값만 봅니다)
 */
public record DrainAssessmentRequest(
        @NotBlank(message = "판독할 사진을 등록해 주세요.")
        String photoUrl
) {
}
