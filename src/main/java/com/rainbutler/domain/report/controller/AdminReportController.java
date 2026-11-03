package com.rainbutler.domain.report.controller;

import com.rainbutler.domain.report.dto.ReportDetailResponse;
import com.rainbutler.domain.report.dto.ReportListResponse;
import com.rainbutler.domain.report.dto.ReportReviewRequest;
import com.rainbutler.domain.report.entity.ReportStatus;
import com.rainbutler.domain.report.service.ReportQueryService;
import com.rainbutler.domain.report.service.ReportService;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.response.ApiResponse;
import com.rainbutler.global.security.LoginUserId;
import com.rainbutler.global.swagger.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 제보 처리 API입니다.
 */
@Tag(name = "Admin", description = "관리자")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/reports")
public class AdminReportController {

    private final ReportQueryService reportQueryService;
    private final ReportService reportService;

    @Operation(summary = "제보 목록 조회 (관리자)", description = "제보를 최신순으로 조회합니다. "
            + "status를 보내면 그 상태의 제보만 조회합니다. ADMIN · CITY_OFFICIAL만 쓸 수 있습니다.")
    @ApiErrorCodes({ErrorCode.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN, ErrorCode.INVALID_TOKEN,
            ErrorCode.INVALID_TYPE, ErrorCode.FORBIDDEN})
    @GetMapping
    public ApiResponse<ReportListResponse> findAll(
            @RequestParam(required = false) ReportStatus status
    ) {
        return ApiResponse.ok(reportQueryService.findAll(status));
    }

    @Operation(summary = "제보 승인 · 반려 (관리자)", description = "제보를 승인하거나 반려합니다. "
            + "승인하면 제보자에게 경험치가 지급되고, 반려는 사유가 필요합니다. "
            + "이미 처리된 제보는 다시 처리할 수 없습니다. ADMIN · CITY_OFFICIAL만 쓸 수 있습니다.")
    @ApiErrorCodes({ErrorCode.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN, ErrorCode.INVALID_TOKEN,
            ErrorCode.INVALID_INPUT, ErrorCode.USER_NOT_FOUND, ErrorCode.REPORT_NOT_FOUND,
            ErrorCode.REPORT_ALREADY_PROCESSED, ErrorCode.FORBIDDEN})
    @PatchMapping("/{reportId}")
    public ApiResponse<ReportDetailResponse> review(
            @LoginUserId Long userId,
            @PathVariable Long reportId,
            @Valid @RequestBody ReportReviewRequest request
    ) {
        return ApiResponse.ok(reportService.review(userId, reportId, request));
    }
}
