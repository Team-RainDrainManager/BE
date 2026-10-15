package com.rainbutler.domain.drain.dto.Request;

import jakarta.validation.constraints.NotBlank;

/**
 * 빗물받이 사진 단독 판독 요청입니다. (생성 없이 판정값만 봅니다)
 *
 * <p>사진은 {@code POST /api/v1/uploads/presigned-url}로 S3에 먼저 올리고, 받은 {@code key}를
 * {@code photoKey}로 보냅니다. 용도(purpose)는 DRAIN · BEFORE · AFTER 중 하나여야 합니다.
 */
public record DrainAssessmentRequest(
        @NotBlank(message = "판독할 사진을 등록해 주세요.")
        String photoKey
) {
}
