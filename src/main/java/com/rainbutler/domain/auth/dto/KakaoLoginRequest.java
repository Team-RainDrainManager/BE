package com.rainbutler.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 카카오 로그인 요청입니다. 프론트가 카카오에서 받은 인가 코드를 보냅니다.
 *
 * @param code 카카오 인가 코드
 * @param redirectUri 인가 요청 때 쓴 redirectUri (허용 목록과 정확히 일치해야 함)
 */
public record KakaoLoginRequest(
        @NotBlank String code,
        @Schema(example = "http://localhost:5173/oauth/kakao") @NotBlank String redirectUri) {
}
