package com.rainbutler.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * refresh 토큰을 담는 요청입니다. (재발급 · 로그아웃)
 *
 * @param refreshToken 로그인 때 받은 refresh 토큰
 */
public record RefreshTokenRequest(@NotBlank String refreshToken) {
}
