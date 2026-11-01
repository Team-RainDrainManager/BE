package com.rainbutler.domain.adoption.controller;

import com.rainbutler.domain.adoption.dto.Request.ManagementLogRequest;
import com.rainbutler.domain.adoption.dto.Response.ManagementLogResponse;
import com.rainbutler.domain.adoption.service.ManagementLogService;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.response.ApiResponse;
import com.rainbutler.global.security.LoginUserId;
import com.rainbutler.global.swagger.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Adoption", description = "입양")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/adoptions/{adoptionId}/management-logs")
public class ManagementLogController {

    private final ManagementLogService managementLogService;

    @Operation(summary = "관리 기록", description = "입양한 빗물받이의 정기 · 비 예보 점검(청소) 기록을 남깁니다. "
            + "AI가 사진을 판정하고, 위치가 너무 멀거나 확신도가 낮으면 관리자 검토 대기로 남깁니다. "
            + "인증에 성공하면 마지막 관리일 · 다음 관리 예정일이 갱신됩니다. 로그인이 필요합니다.")
    @ApiErrorCodes({ErrorCode.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN, ErrorCode.INVALID_TOKEN,
            ErrorCode.INVALID_INPUT, ErrorCode.FORBIDDEN, ErrorCode.ADOPTION_NOT_FOUND,
            ErrorCode.REQUEST_NOT_FOUND, ErrorCode.REQUEST_NOT_COMPLETABLE,
            ErrorCode.DUPLICATE_RESOURCE, ErrorCode.DUPLICATE_PHOTO,
            ErrorCode.INVALID_PHOTO_KEY, ErrorCode.PHOTO_FORBIDDEN, ErrorCode.PHOTO_NOT_UPLOADED,
            ErrorCode.UNSUPPORTED_IMAGE_TYPE, ErrorCode.PHOTO_TOO_LARGE, ErrorCode.STORAGE_ERROR,
            ErrorCode.STORAGE_NOT_CONFIGURED})
    @PostMapping
    public ApiResponse<ManagementLogResponse> recordManagementLog(
            @LoginUserId Long userId,
            @PathVariable Long adoptionId,
            @Valid @RequestBody ManagementLogRequest request
    ) {
        ManagementLogResponse response =
                managementLogService.record(userId, adoptionId, request);

        return ApiResponse.ok(response);
    }
}
