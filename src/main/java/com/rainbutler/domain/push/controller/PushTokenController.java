package com.rainbutler.domain.push.controller;

import com.rainbutler.domain.push.dto.FcmTokenDeleteRequest;
import com.rainbutler.domain.push.dto.FcmTokenRegisterRequest;
import com.rainbutler.domain.push.service.FcmTokenService;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.response.ApiResponse;
import com.rainbutler.global.security.LoginUserId;
import com.rainbutler.global.swagger.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FCM 기기 토큰 API입니다.
 */
@Tag(name = "Push", description = "푸시 알림 (FCM 기기 토큰)")
@RestController
@RequestMapping("/api/v1/push/tokens")
@RequiredArgsConstructor
public class PushTokenController {

    private final FcmTokenService fcmTokenService;

    /**
     * FCM 토큰을 등록 · 갱신합니다.
     */
    @Operation(summary = "FCM 토큰 등록 · 갱신",
            description = "앱 시작 · 로그인 때마다 호출합니다. (토큰은 바뀔 수 있음) "
                    + "같은 토큰이 이미 있으면 현재 사용자로 옮깁니다.")
    @ApiErrorCodes({ErrorCode.INVALID_INPUT, ErrorCode.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN,
            ErrorCode.INVALID_TOKEN})
    @PutMapping
    public ResponseEntity<ApiResponse<Void>> register(
            @LoginUserId Long userId, @Valid @RequestBody FcmTokenRegisterRequest request) {
        fcmTokenService.register(userId, request.token(), request.platform());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /**
     * FCM 토큰을 삭제합니다.
     */
    @Operation(summary = "FCM 토큰 삭제",
            description = "로그아웃 전에 호출합니다. 내 토큰이 아니거나 없으면 무시하고 성공으로 응답합니다.")
    @ApiErrorCodes({ErrorCode.INVALID_INPUT, ErrorCode.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN,
            ErrorCode.INVALID_TOKEN})
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> delete(
            @LoginUserId Long userId, @Valid @RequestBody FcmTokenDeleteRequest request) {
        fcmTokenService.delete(userId, request.token());
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
