package com.rainbutler.domain.report.controller;

import com.rainbutler.domain.report.dto.ReportCreateRequest;
import com.rainbutler.domain.report.dto.ReportCreateResponse;
import com.rainbutler.domain.report.dto.ReportDetailResponse;
import com.rainbutler.domain.report.dto.ReportListResponse;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자 제보 API입니다.
 */
@Tag(name = "Report", description = "제보")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;
    private final ReportQueryService reportQueryService;

    @Operation(summary = "제보 생성", description = "막힘 · 신규 · 위치 수정 · 철거 · 악취 제보를 접수합니다. "
            + "제보 종류에 따라 사진 · 설명 · 새 위치가 필요합니다. "
            + "접수 상태는 PENDING이고, 관리자가 승인하면 경험치가 지급됩니다. 로그인이 필요합니다.")
    @ApiErrorCodes({ErrorCode.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN, ErrorCode.INVALID_TOKEN,
            ErrorCode.INVALID_INPUT, ErrorCode.USER_NOT_FOUND, ErrorCode.DRAIN_NOT_FOUND,
            ErrorCode.PHOTO_FORBIDDEN, ErrorCode.INVALID_PHOTO_KEY,
            ErrorCode.PHOTO_NOT_UPLOADED, ErrorCode.UNSUPPORTED_IMAGE_TYPE,
            ErrorCode.PHOTO_TOO_LARGE, ErrorCode.STORAGE_ERROR,
            ErrorCode.STORAGE_NOT_CONFIGURED})
    @PostMapping
    public ApiResponse<ReportCreateResponse> create(
            @LoginUserId Long userId,
            @Valid @RequestBody ReportCreateRequest request
    ) {
        return ApiResponse.ok(reportService.create(userId, request));
    }

    @Operation(summary = "내 제보 내역 조회", description = "내가 접수한 제보를 최신순으로 조회합니다. "
            + "로그인이 필요합니다.")
    @ApiErrorCodes({ErrorCode.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN, ErrorCode.INVALID_TOKEN})
    @GetMapping("/me")
    public ApiResponse<ReportListResponse> findMine(@LoginUserId Long userId) {
        return ApiResponse.ok(reportQueryService.findMine(userId));
    }

    @Operation(summary = "제보 상세 조회", description = "제보 한 건을 조회합니다. "
            + "제보자 본인과 관리자(ADMIN · CITY_OFFICIAL)만 볼 수 있습니다. 로그인이 필요합니다.")
    @ApiErrorCodes({ErrorCode.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN, ErrorCode.INVALID_TOKEN,
            ErrorCode.USER_NOT_FOUND, ErrorCode.REPORT_NOT_FOUND, ErrorCode.FORBIDDEN})
    @GetMapping("/{reportId}")
    public ApiResponse<ReportDetailResponse> findDetail(
            @LoginUserId Long userId,
            @PathVariable Long reportId
    ) {
        return ApiResponse.ok(reportQueryService.findDetail(userId, reportId));
    }
}
