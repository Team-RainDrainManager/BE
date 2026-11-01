package com.rainbutler.domain.inspection.controller;

import com.rainbutler.domain.inspection.dto.InspectionEligibilityResponse;
import com.rainbutler.domain.inspection.service.InspectionEligibilityService;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.response.ApiResponse;
import com.rainbutler.global.security.LoginUserId;
import com.rainbutler.global.swagger.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 점검 화면 진입 전 권한을 확인하는 API입니다. */
@Tag(name = "Inspection", description = "점검")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/drains/{drainId}/inspection-eligibility")
public class InspectionEligibilityController {

    private static final String DESCRIPTION = """
            점검 화면에 진입하기 전 현재 사용자가 점검할 수 있는지 확인합니다.

            - `requestId` 없이 호출하면 본인이 입양한 빗물받이의 정기 점검만 허용합니다.
            - `requestId`가 있으면 비 예보 요청의 `ACCEPTED` 입양자 또는 `CLAIMED` 대신 점검자만 허용합니다.
            - 제재 중이거나, 요청이 종료됐거나, 완료 마감이 지나면 점검할 수 없습니다.
            - 실제 점검 제출 시에는 서버가 같은 조건을 다시 검증합니다.
            """;

    private final InspectionEligibilityService inspectionEligibilityService;

    @Operation(summary = "점검 가능 여부 확인", description = DESCRIPTION)
    @ApiErrorCodes({ErrorCode.DRAIN_NOT_FOUND, ErrorCode.REQUEST_NOT_FOUND,
            ErrorCode.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN, ErrorCode.INVALID_TOKEN})
    @GetMapping
    public ResponseEntity<ApiResponse<InspectionEligibilityResponse>> check(
            @LoginUserId Long userId,
            @PathVariable Long drainId,
            @RequestParam(required = false) Long requestId) {
        return ResponseEntity.ok(ApiResponse.ok(
                inspectionEligibilityService.check(userId, drainId, requestId)));
    }
}
