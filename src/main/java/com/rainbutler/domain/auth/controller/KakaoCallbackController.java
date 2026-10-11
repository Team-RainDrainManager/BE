package com.rainbutler.domain.auth.controller;

import com.rainbutler.domain.auth.dto.KakaoLoginResponse;
import com.rainbutler.domain.auth.service.KakaoAuthService;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.response.ApiResponse;
import com.rainbutler.global.swagger.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * 프론트 없이 카카오 로그인을 확인하는 콜백입니다. (local · dev 프로필에서만 동작)
 *
 * <p>사용법: 아래 주소로 받은 카카오 인가 URL을 브라우저에서 열고 카카오 로그인 →
 * 이 콜백이 토큰 JSON을 돌려줍니다.
 * <pre>
 * GET /api/v1/auth/kakao/login-url
 *     ?redirectUri=http://localhost:8080/api/v1/auth/kakao/callback
 * </pre>
 */
@Profile({"local", "dev"})
@Tag(name = "Auth", description = "회원가입 · 로그인 · 토큰 재발급 · 로그아웃")
@SecurityRequirements
@RestController
@RequestMapping("/api/v1/auth/kakao")
@RequiredArgsConstructor
public class KakaoCallbackController {

    private final KakaoAuthService kakaoAuthService;

    /**
     * 카카오가 돌려준 인가 코드로 로그인합니다. redirectUri는 이 콜백 주소로 고정됩니다.
     */
    @Operation(summary = "[local · dev] 카카오 로그인 콜백 (테스트용)")
    @ApiErrorCodes({ErrorCode.MISSING_PARAMETER, ErrorCode.INVALID_REDIRECT_URI,
            ErrorCode.KAKAO_AUTH_FAILED, ErrorCode.KAKAO_API_ERROR})
    @GetMapping("/callback")
    public ResponseEntity<ApiResponse<KakaoLoginResponse>> callback(@RequestParam String code) {
        String redirectUri = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .replaceQuery(null)
                .toUriString();
        return ResponseEntity.ok(ApiResponse.ok(kakaoAuthService.login(code, redirectUri)));
    }
}
