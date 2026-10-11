package com.rainbutler.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 자체 회원가입 요청입니다.
 *
 * @param email 이메일 (최대 100자)
 * @param password 비밀번호 (8~64자, 영문 · 숫자 각 1개 이상)
 * @param nickname 닉네임 (2~20자, 앞뒤 공백 제거)
 */
public record SignupRequest(
        @Schema(example = "butler@example.com")
        @NotBlank @Email @Size(max = 100)
        String email,

        @Schema(example = "rain1234")
        @NotBlank
        @Size(min = 8, max = 64)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$", message = "영문과 숫자를 각각 1개 이상 포함해야 합니다")
        String password,

        @Schema(example = "빗물집사")
        @NotBlank @Size(min = 2, max = 20)
        String nickname) {

    /** 검증 전에 앞뒤 공백을 지웁니다. */
    public SignupRequest {
        email = email == null ? null : email.strip();
        nickname = nickname == null ? null : nickname.strip();
    }
}
