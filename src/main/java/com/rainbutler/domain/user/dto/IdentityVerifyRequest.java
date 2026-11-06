package com.rainbutler.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 실명 등록 요청입니다. (활동 확인서 발급용)
 *
 * <p>지금은 본인인증 대행사 연동 전이라 이름만 받는 목업입니다.
 *
 * @param realName 활동 확인서에 표시할 실명 (1~20자)
 */
public record IdentityVerifyRequest(
        @NotBlank(message = "성명을 입력해 주세요.")
        @Size(max = 20, message = "성명은 20자 이내로 입력해 주세요.")
        @Schema(example = "홍길동") String realName) {
}
