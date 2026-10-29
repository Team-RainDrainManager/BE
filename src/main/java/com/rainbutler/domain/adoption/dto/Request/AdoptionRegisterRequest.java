package com.rainbutler.domain.adoption.dto.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 입양 등록 요청입니다.
 *
 * <p>인증 통과한 청소를 통해 빗물받이를 입양합니다.
 */
public record AdoptionRegisterRequest(
        @NotNull(message = "청소 ID를 입력해 주세요.")
        Long cleaningId,

        @NotBlank(message = "별명을 입력해 주세요.")
        @Size(min = 1, max = 20, message = "별명은 1~20자로 입력해 주세요.")
        String nickname,

        @NotNull(message = "안전 동의가 필요합니다.")
        Boolean safetyAgreed
) {
}
