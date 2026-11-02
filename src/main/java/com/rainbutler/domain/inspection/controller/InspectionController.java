package com.rainbutler.domain.inspection.controller;

import com.rainbutler.domain.inspection.dto.InspectionCompleteRequest;
import com.rainbutler.domain.inspection.dto.InspectionDetailResponse;
import com.rainbutler.domain.inspection.dto.InspectionStartRequest;
import com.rainbutler.domain.inspection.dto.InspectionSubmitResponse;
import com.rainbutler.domain.inspection.service.InspectionQueryService;
import com.rainbutler.domain.inspection.service.InspectionService;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.response.ApiResponse;
import com.rainbutler.global.security.LoginUserId;
import com.rainbutler.global.swagger.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 점검 · 청소 기록 공통 제출 API입니다. */
@Tag(name = "Inspection", description = "점검")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/inspections")
public class InspectionController {

    private final InspectionService inspectionService;
    private final InspectionQueryService inspectionQueryService;

    @Operation(summary = "점검 기록 상세 조회", description = "점검 기록 한 건을 조회합니다. "
            + "점검한 본인 · 해당 입양의 입양자 · 관리자(ADMIN · CITY_OFFICIAL)만 볼 수 있습니다. "
            + "사진은 조회 URL로 내려주며, 촬영일로부터 보존 기간(365일)이 지나면 "
            + "photoDeleted=true이고 사진 URL은 null입니다. 로그인이 필요합니다.")
    @ApiErrorCodes({ErrorCode.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN, ErrorCode.INVALID_TOKEN,
            ErrorCode.USER_NOT_FOUND, ErrorCode.INSPECTION_NOT_FOUND, ErrorCode.FORBIDDEN})
    @GetMapping("/{inspectionId}")
    public ApiResponse<InspectionDetailResponse> findDetail(
            @LoginUserId Long userId,
            @PathVariable Long inspectionId
    ) {
        return ApiResponse.ok(inspectionQueryService.findDetail(userId, inspectionId));
    }

    @Operation(summary = "점검 시작", description = "점검 전 사진으로 정기 · 비 예보 · 대신 점검을 제출합니다. "
            + "AI가 사진을 판정하고, 깨끗하면 바로 점검을 마치며 더러우면 청소 후 사진을 받습니다. "
            + "위치가 너무 멀거나 AI 확신도가 낮으면 관리자 검토 대기(PENDING)로 남깁니다. "
            + "입양자 · 요청 상태 · 마감 · 제재 여부를 제출 시 다시 확인합니다. 로그인이 필요합니다.")
    @ApiErrorCodes({ErrorCode.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN, ErrorCode.INVALID_TOKEN,
            ErrorCode.INVALID_INPUT, ErrorCode.USER_NOT_FOUND, ErrorCode.USER_SANCTIONED,
            ErrorCode.DRAIN_NOT_FOUND, ErrorCode.FORBIDDEN, ErrorCode.REQUEST_NOT_FOUND,
            ErrorCode.REQUEST_NOT_COMPLETABLE, ErrorCode.DUPLICATE_RESOURCE,
            ErrorCode.DUPLICATE_PHOTO, ErrorCode.INVALID_PHOTO_KEY, ErrorCode.PHOTO_FORBIDDEN,
            ErrorCode.PHOTO_NOT_UPLOADED, ErrorCode.UNSUPPORTED_IMAGE_TYPE,
            ErrorCode.PHOTO_TOO_LARGE, ErrorCode.STORAGE_ERROR,
            ErrorCode.STORAGE_NOT_CONFIGURED})
    @PostMapping
    public ApiResponse<InspectionSubmitResponse> start(
            @LoginUserId Long userId,
            @Valid @RequestBody InspectionStartRequest request
    ) {
        return ApiResponse.ok(inspectionService.start(userId, request));
    }

    @Operation(summary = "정기 점검 청소 후 인증", description = "입양 후 정기 점검 흐름의 청소 후 인증입니다. "
            + "점검 전 사진에서 더러움(1~3)이 확인된 점검에 청소 후 사진을 붙입니다. "
            + "청소 후 등급이 점검 전보다 좋아지면 인증을 통과하며 관리 주기 · 빗물받이 사진 · 경험치가 갱신됩니다. "
            + "좋아지지 않으면 다시 청소할 수 있습니다. 위치 불일치 · 낮은 확신도면 관리자 검토 대기(PENDING)로 남깁니다. "
            + "로그인이 필요합니다.")
    @ApiErrorCodes({ErrorCode.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN, ErrorCode.INVALID_TOKEN,
            ErrorCode.INVALID_INPUT, ErrorCode.INSPECTION_NOT_FOUND,
            ErrorCode.INSPECTION_ALREADY_COMPLETED, ErrorCode.FORBIDDEN,
            ErrorCode.DUPLICATE_PHOTO, ErrorCode.INVALID_PHOTO_KEY, ErrorCode.PHOTO_FORBIDDEN,
            ErrorCode.PHOTO_NOT_UPLOADED, ErrorCode.UNSUPPORTED_IMAGE_TYPE,
            ErrorCode.PHOTO_TOO_LARGE, ErrorCode.STORAGE_ERROR,
            ErrorCode.STORAGE_NOT_CONFIGURED})
    @PostMapping("/{inspectionId}/complete")
    public ApiResponse<InspectionSubmitResponse> complete(
            @LoginUserId Long userId,
            @PathVariable Long inspectionId,
            @Valid @RequestBody InspectionCompleteRequest request
    ) {
        return ApiResponse.ok(inspectionService.complete(userId, inspectionId, request));
    }
}
