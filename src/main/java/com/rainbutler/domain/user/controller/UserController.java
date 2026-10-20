package com.rainbutler.domain.user.controller;

import com.rainbutler.domain.user.dto.MeResponse;
import com.rainbutler.domain.user.dto.NicknameUpdateRequest;
import com.rainbutler.domain.user.dto.NicknameUpdateResponse;
import com.rainbutler.domain.user.service.UserService;
import com.rainbutler.domain.user.service.WithdrawalService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자 API입니다.
 */
@Tag(name = "User", description = "내 정보")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final WithdrawalService withdrawalService;

    /**
     * 로그인 사용자의 정보를 조회합니다.
     */
    @Operation(summary = "내 정보 조회")
    @ApiErrorCodes({ErrorCode.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN, ErrorCode.INVALID_TOKEN,
            ErrorCode.USER_NOT_FOUND})
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MeResponse>> me(@LoginUserId Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getMe(userId)));
    }

    /**
     * 로그인 사용자의 닉네임을 바꿉니다.
     */
    @Operation(summary = "닉네임 수정",
            description = "가입과 같은 규칙(앞뒤 공백 제거 후 2~20자). 중복 허용, 같은 닉네임으로 바꿔도 성공합니다.")
    @ApiErrorCodes({ErrorCode.INVALID_INPUT, ErrorCode.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN,
            ErrorCode.INVALID_TOKEN, ErrorCode.USER_NOT_FOUND})
    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<NicknameUpdateResponse>> updateNickname(
            @LoginUserId Long userId, @Valid @RequestBody NicknameUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userService.updateNickname(userId, request)));
    }

    /**
     * 회원 탈퇴합니다.
     */
    @Operation(summary = "회원 탈퇴",
            description = "개인정보를 지우고 탈퇴합니다. 활동 기록(청소 · 점검 · 제보 · 포인트)은 남습니다. "
                    + "입양은 모두 해제되고, 맡은 대신 점검은 다시 공개됩니다. "
                    + "같은 카카오 계정 · 이메일로 다시 오면 새 계정으로 가입됩니다 (이전 기록은 이어지지 않음). "
                    + "빌린 도구가 있으면 반납한 뒤 탈퇴할 수 있습니다.")
    @ApiErrorCodes({ErrorCode.WITHDRAW_TOOL_NOT_RETURNED, ErrorCode.UNAUTHORIZED,
            ErrorCode.EXPIRED_TOKEN, ErrorCode.INVALID_TOKEN, ErrorCode.USER_NOT_FOUND})
    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> withdraw(@LoginUserId Long userId) {
        withdrawalService.withdraw(userId);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
