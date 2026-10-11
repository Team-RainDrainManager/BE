package com.rainbutler.domain.auth.controller;

import com.rainbutler.domain.auth.dto.LoginRequest;
import com.rainbutler.domain.auth.dto.RefreshTokenRequest;
import com.rainbutler.domain.auth.dto.SignupRequest;
import com.rainbutler.domain.auth.dto.TokenResponse;
import com.rainbutler.domain.auth.service.AuthService;
import com.rainbutler.domain.auth.service.TokenService;
import com.rainbutler.global.response.ApiResponse;
import com.rainbutler.global.security.LoginUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 자체 회원가입 · 로그인 · 토큰 재발급 · 로그아웃 API입니다.
 */
@Tag(name = "Auth", description = "회원가입 · 로그인 · 토큰 재발급 · 로그아웃")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;

    /**
     * 이메일 · 비밀번호로 가입하고 토큰을 발급합니다.
     */
    @Operation(summary = "자체 회원가입 (가입 후 바로 토큰 발급)")
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<TokenResponse>> signup(
            @Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(authService.signup(request)));
    }

    /**
     * 이메일 · 비밀번호로 로그인합니다.
     */
    @Operation(summary = "자체 로그인")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.login(request)));
    }

    /**
     * refresh 토큰으로 토큰을 재발급합니다. (기존 refresh는 폐기)
     */
    @Operation(summary = "토큰 재발급 (refresh 회전)")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(tokenService.rotate(request.refreshToken())));
    }

    /**
     * 로그아웃: 보낸 refresh 토큰을 폐기합니다. access 토큰은 만료될 때까지 유효합니다.
     */
    @Operation(summary = "로그아웃 (refresh 토큰 폐기)")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @LoginUserId Long userId, @Valid @RequestBody RefreshTokenRequest request) {
        tokenService.revoke(userId, request.refreshToken());
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
