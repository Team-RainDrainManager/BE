package com.rainbutler.domain.user.controller;

import com.rainbutler.domain.user.dto.OnboardingRequest;
import com.rainbutler.domain.user.dto.OnboardingStatusResponse;
import com.rainbutler.domain.user.service.OnboardingService;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.response.ApiResponse;
import com.rainbutler.global.security.LoginUserId;
import com.rainbutler.global.swagger.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 온보딩 API입니다. 가입 직후 한 번 약관 동의와 본인인증을 마칩니다.
 */
@Tag(name = "Onboarding", description = "온보딩 (약관 동의 · 본인인증)")
@RestController
@RequestMapping("/api/v1/users/me/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private static final String COMPLETE_DESCRIPTION = """
            약관 동의 + 본인인증을 한 번에 마칩니다.

            - `agreements`: `GET /api/v1/terms`의 **모든** 약관에 대한 동의 여부 (선택 약관 미동의도 `false`로)
            - 선택 약관 동의값이 알림 설정이 됩니다. (NOTIFICATION → 활동 알림, NIGHT_PUSH → 야간 푸시)
            - **본인인증은 시연용 목업: 개인정보 없이 완료 처리**합니다. `identityVerified: true`를 보내면 됩니다.

            **본인인증 안내 문구**: "빗물받이는 모두가 함께 쓰는 공공시설이에요. 안전하고 책임 있는 관리를 위해 본인인증을 한 번 진행해 주세요."
            """;

    private final OnboardingService onboardingService;

    /**
     * 온보딩을 완료합니다.
     */
    @Operation(summary = "온보딩 완료 (약관 동의 + 본인인증 목업)", description = COMPLETE_DESCRIPTION)
    @ApiErrorCodes({ErrorCode.INVALID_INPUT, ErrorCode.REQUIRED_TERMS_NOT_AGREED,
            ErrorCode.TERMS_OUTDATED, ErrorCode.IDENTITY_VERIFICATION_REQUIRED_ON_ONBOARDING,
            ErrorCode.ONBOARDING_ALREADY_COMPLETED, ErrorCode.UNAUTHORIZED,
            ErrorCode.EXPIRED_TOKEN, ErrorCode.INVALID_TOKEN})
    @PostMapping
    public ResponseEntity<ApiResponse<OnboardingStatusResponse>> complete(
            @LoginUserId Long userId, @Valid @RequestBody OnboardingRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(onboardingService.complete(userId, request)));
    }

    /**
     * 온보딩 상태를 조회합니다.
     */
    @Operation(summary = "온보딩 상태 조회",
            description = "`pendingRequiredTerms`: 아직 동의하지 않은(또는 새 버전이 나온) 필수 약관")
    @ApiErrorCodes({ErrorCode.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN, ErrorCode.INVALID_TOKEN})
    @GetMapping
    public ResponseEntity<ApiResponse<OnboardingStatusResponse>> getStatus(
            @LoginUserId Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(onboardingService.getStatus(userId)));
    }
}
