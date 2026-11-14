package com.rainbutler.domain.user.controller;

import com.rainbutler.domain.user.dto.IdentityVerifyRequest;
import com.rainbutler.domain.user.dto.IdentityVerifyResponse;
import com.rainbutler.domain.user.dto.MeResponse;
import com.rainbutler.domain.user.dto.NicknameUpdateRequest;
import com.rainbutler.domain.user.dto.NicknameUpdateResponse;
import com.rainbutler.domain.user.dto.ProfileImageUpdateRequest;
import com.rainbutler.domain.user.dto.ProfileImageUpdateResponse;
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
import org.springframework.web.bind.annotation.PostMapping;
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
     * 닉네임 중복 여부를 확인합니다.
     */
    @Operation(summary = "닉네임 중복 확인",
            description = "닉네임이 이미 사용 중인지 확인합니다. 사용 중이면 false를 반환합니다.")
    @ApiErrorCodes({ErrorCode.INVALID_INPUT})
    @GetMapping("/nickname/available")
    public ResponseEntity<ApiResponse<Boolean>> checkNicknameAvailable(
            @org.springframework.web.bind.annotation.RequestParam String nickname) {
        return ResponseEntity.ok(ApiResponse.ok(userService.isNicknameAvailable(nickname)));
    }

    /**
     * 활동 확인서에 쓸 실명을 등록합니다. (목업 본인인증)
     */
    @Operation(summary = "실명 등록",
            description = "활동 확인서에 표시할 실명을 저장합니다. 지금은 본인인증 대행사 연동 전이라 "
                    + "이름만 받는 목업이며, 실제 인증을 붙일 때 같은 자리에서 결과를 검증합니다.")
    @ApiErrorCodes({ErrorCode.INVALID_INPUT, ErrorCode.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN,
            ErrorCode.INVALID_TOKEN, ErrorCode.USER_NOT_FOUND})
    @PostMapping("/me/identity")
    public ResponseEntity<ApiResponse<IdentityVerifyResponse>> verifyIdentity(
            @LoginUserId Long userId, @Valid @RequestBody IdentityVerifyRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userService.verifyIdentity(userId, request)));
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
     * 마이페이지 프로필 사진을 등록 · 변경합니다.
     */
    @Operation(summary = "프로필 사진 변경",
            description = "`POST /uploads/presigned-url`(purpose=PROFILE)로 사진을 올린 뒤 받은 key를 "
                    + "보냅니다. 기존 사진이 있으면 교체하고, 응답에는 조회 URL을 내려줍니다.")
    @ApiErrorCodes({ErrorCode.INVALID_INPUT, ErrorCode.INVALID_PHOTO_KEY, ErrorCode.PHOTO_FORBIDDEN,
            ErrorCode.PHOTO_NOT_UPLOADED, ErrorCode.UNSUPPORTED_IMAGE_TYPE, ErrorCode.PHOTO_TOO_LARGE,
            ErrorCode.STORAGE_ERROR, ErrorCode.STORAGE_NOT_CONFIGURED, ErrorCode.UNAUTHORIZED,
            ErrorCode.EXPIRED_TOKEN, ErrorCode.INVALID_TOKEN, ErrorCode.USER_NOT_FOUND})
    @PatchMapping("/me/profile-image")
    public ResponseEntity<ApiResponse<ProfileImageUpdateResponse>> updateProfileImage(
            @LoginUserId Long userId, @Valid @RequestBody ProfileImageUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userService.updateProfileImage(userId, request)));
    }

    /**
     * 프로필 사진을 삭제하고 기본 프로필로 되돌립니다.
     */
    @Operation(summary = "프로필 사진 삭제",
            description = "프로필 사진을 지우고 기본 프로필로 되돌립니다. 이미 없어도 성공합니다.")
    @ApiErrorCodes({ErrorCode.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN, ErrorCode.INVALID_TOKEN,
            ErrorCode.USER_NOT_FOUND})
    @DeleteMapping("/me/profile-image")
    public ResponseEntity<ApiResponse<ProfileImageUpdateResponse>> deleteProfileImage(
            @LoginUserId Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(userService.deleteProfileImage(userId)));
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
