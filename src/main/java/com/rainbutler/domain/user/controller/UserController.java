package com.rainbutler.domain.user.controller;

import com.rainbutler.domain.user.dto.MeResponse;
import com.rainbutler.domain.user.dto.NicknameUpdateRequest;
import com.rainbutler.domain.user.dto.NicknameUpdateResponse;
import com.rainbutler.domain.user.service.UserService;
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
}
