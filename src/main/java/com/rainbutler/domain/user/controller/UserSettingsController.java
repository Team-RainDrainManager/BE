package com.rainbutler.domain.user.controller;

import com.rainbutler.domain.user.dto.NotificationSettingsResponse;
import com.rainbutler.domain.user.dto.NotificationSettingsUpdateRequest;
import com.rainbutler.domain.user.service.UserSettingsService;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.response.ApiResponse;
import com.rainbutler.global.security.LoginUserId;
import com.rainbutler.global.swagger.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 알림 설정 API입니다.
 */
@Tag(name = "User", description = "내 정보")
@RestController
@RequestMapping("/api/v1/users/me/settings")
@RequiredArgsConstructor
public class UserSettingsController {

    private static final String DESCRIPTION = """
            - `notifications`: 활동 알림 수신. 끄면 알림을 **만들지도 보내지도 않습니다**.
            - `nightConsent`: 야간(22시~08시) 푸시 수신. 끄면 야간에는 푸시 없이 알림함에만 저장합니다.

            초기값은 온보딩의 선택 약관 동의로 정해집니다.
            """;

    private final UserSettingsService userSettingsService;

    /**
     * 알림 설정을 조회합니다.
     */
    @Operation(summary = "알림 설정 조회", description = DESCRIPTION)
    @ApiErrorCodes({ErrorCode.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN, ErrorCode.INVALID_TOKEN,
            ErrorCode.USER_NOT_FOUND})
    @GetMapping
    public ResponseEntity<ApiResponse<NotificationSettingsResponse>> get(
            @LoginUserId Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(userSettingsService.get(userId)));
    }

    /**
     * 알림 설정을 바꿉니다. 보낸 값만 바꿉니다.
     */
    @Operation(summary = "알림 설정 변경",
            description = "보낸 값만 바꿉니다 (null · 빠진 값은 유지). 둘 다 없으면 400. "
                    + "값이 바뀌면 동의 · 철회 이력이 남습니다.")
    @ApiErrorCodes({ErrorCode.INVALID_INPUT, ErrorCode.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN,
            ErrorCode.INVALID_TOKEN, ErrorCode.USER_NOT_FOUND})
    @PatchMapping
    public ResponseEntity<ApiResponse<NotificationSettingsResponse>> update(
            @LoginUserId Long userId, @RequestBody NotificationSettingsUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userSettingsService.update(userId, request)));
    }
}
