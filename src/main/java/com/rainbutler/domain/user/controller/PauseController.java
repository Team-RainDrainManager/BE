package com.rainbutler.domain.user.controller;

import com.rainbutler.domain.user.dto.PauseResponse;
import com.rainbutler.domain.user.dto.PauseStatusResponse;
import com.rainbutler.domain.user.service.PauseService;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.response.ApiResponse;
import com.rainbutler.global.security.LoginUserId;
import com.rainbutler.global.swagger.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 점검 쉬기 API입니다.
 */
@Tag(name = "User", description = "내 정보")
@RestController
@RequestMapping("/api/v1/users/me/pause")
@RequiredArgsConstructor
public class PauseController {

    private static final String POLICY = """
            입양자가 14일 동안 정기 점검을 쉽니다. 90일에 한 번, 취소 · 연장은 없습니다.
            쉬는 동안 정기 점검 주기 · 미관리 경고 · 자동 해제는 보류되고, **비 예보 점검 요청은 계속 옵니다**.
            """;

    private final PauseService pauseService;

    /**
     * 점검 쉬기 상태를 조회합니다.
     */
    @Operation(summary = "점검 쉬기 상태 조회", description = POLICY
            + "\n`reason`: 신청할 수 없는 이유 (ALREADY_ACTIVE · NO_ADOPTION · COOLDOWN, 가능하면 null)")
    @ApiErrorCodes({ErrorCode.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN, ErrorCode.INVALID_TOKEN,
            ErrorCode.USER_NOT_FOUND})
    @GetMapping
    public ResponseEntity<ApiResponse<PauseStatusResponse>> getStatus(@LoginUserId Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(pauseService.getStatus(userId)));
    }

    /**
     * 점검 쉬기를 신청합니다.
     */
    @Operation(summary = "점검 쉬기 신청", description = POLICY
            + "\n쿨다운 중이면 409 `PAUSE_COOLDOWN`과 함께 `error.details.nextAvailableAt`을 줍니다.")
    @ApiErrorCodes({ErrorCode.ONBOARDING_REQUIRED, ErrorCode.PAUSE_ALREADY_ACTIVE,
            ErrorCode.PAUSE_NO_ADOPTION, ErrorCode.PAUSE_COOLDOWN, ErrorCode.UNAUTHORIZED,
            ErrorCode.EXPIRED_TOKEN, ErrorCode.INVALID_TOKEN})
    @PostMapping
    public ResponseEntity<ApiResponse<PauseResponse>> apply(@LoginUserId Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(pauseService.apply(userId)));
    }
}
