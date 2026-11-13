package com.rainbutler.domain.adoption.controller;

import com.rainbutler.domain.adoption.dto.Request.AdoptionNicknameUpdateRequest;
import com.rainbutler.domain.adoption.dto.Response.AdoptionNicknameUpdateResponse;
import com.rainbutler.domain.adoption.service.AdoptionService;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.response.ApiResponse;
import com.rainbutler.global.security.LoginUserId;
import com.rainbutler.global.swagger.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 입양한 빗물받이의 별명을 바꾸는 API입니다. */
@Tag(name = "Adoption", description = "입양")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/adoptions/{adoptionId}/nickname")
public class AdoptionNicknameController {

    private final AdoptionService adoptionService;

    @Operation(summary = "입양 빗물받이 별명 변경",
            description = "내가 입양한 빗물받이의 별명을 바꿉니다. 입양 등록과 같은 규칙(1~20자)이고, "
                    + "해제된 입양은 바꿀 수 없습니다. 로그인이 필요합니다.")
    @ApiErrorCodes({ErrorCode.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN, ErrorCode.INVALID_TOKEN,
            ErrorCode.INVALID_INPUT, ErrorCode.FORBIDDEN, ErrorCode.ADOPTION_NOT_FOUND})
    @PatchMapping
    public ResponseEntity<ApiResponse<AdoptionNicknameUpdateResponse>> updateNickname(
            @LoginUserId Long userId,
            @PathVariable Long adoptionId,
            @Valid @RequestBody AdoptionNicknameUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                adoptionService.updateNickname(userId, adoptionId, request)));
    }
}
