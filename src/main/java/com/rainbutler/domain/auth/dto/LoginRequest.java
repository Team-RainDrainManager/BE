package com.rainbutler.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 자체 로그인 요청입니다.
 *
 * @param email 이메일
 * @param password 비밀번호
 */
public record LoginRequest(
        @Schema(example = "butler@example.com") @NotBlank String email,
        @Schema(example = "rain1234") @NotBlank String password) {

    /** 검증 전에 이메일 앞뒤 공백을 지웁니다. */
    public LoginRequest {
        email = email == null ? null : email.strip();
    }
}
