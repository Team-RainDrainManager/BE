package com.rainbutler.domain.auth.controller;

import com.rainbutler.domain.auth.client.KakaoOAuthClient;
import com.rainbutler.domain.auth.dto.KakaoLoginRequest;
import com.rainbutler.domain.auth.dto.KakaoLoginResponse;
import com.rainbutler.domain.auth.dto.KakaoLoginUrlResponse;
import com.rainbutler.domain.auth.service.KakaoAuthService;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.response.ApiResponse;
import com.rainbutler.global.swagger.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 카카오 로그인 API입니다.
 */
@Tag(name = "Auth", description = "회원가입 · 로그인 · 토큰 재발급 · 로그아웃")
@SecurityRequirements
@RestController
@RequestMapping("/api/v1/auth/kakao")
@RequiredArgsConstructor
public class KakaoAuthController {

    private final KakaoAuthService kakaoAuthService;
    private final KakaoOAuthClient kakaoOAuthClient;

    /**
     * 카카오 인가 코드로 로그인합니다. 처음이면 가입하고 {@code isNewUser = true}를 돌려줍니다.
     */
    @Operation(summary = "카카오 로그인 (인가 코드 → 토큰 발급)")
    @ApiErrorCodes({ErrorCode.INVALID_INPUT, ErrorCode.INVALID_REDIRECT_URI,
            ErrorCode.KAKAO_AUTH_FAILED, ErrorCode.KAKAO_API_ERROR})
    @PostMapping
    public ResponseEntity<ApiResponse<KakaoLoginResponse>> login(
            @Valid @RequestBody KakaoLoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                kakaoAuthService.login(request.code(), request.redirectUri())));
    }

    /**
     * 카카오 인가 페이지 주소를 돌려줍니다.
     */
    @Operation(summary = "카카오 인가 페이지 주소 조회")
    @ApiErrorCodes({ErrorCode.MISSING_PARAMETER, ErrorCode.INVALID_REDIRECT_URI,
            ErrorCode.KAKAO_API_ERROR})
    @GetMapping("/login-url")
    public ResponseEntity<ApiResponse<KakaoLoginUrlResponse>> loginUrl(
            @RequestParam String redirectUri) {
        return ResponseEntity.ok(ApiResponse.ok(
                new KakaoLoginUrlResponse(kakaoOAuthClient.authorizeUrl(redirectUri))));
    }
}
