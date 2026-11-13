package com.rainbutler.domain.bugreport.controller;

import com.rainbutler.domain.bugreport.dto.AdminBugReportDetailResponse;
import com.rainbutler.domain.bugreport.dto.AdminBugReportResponse;
import com.rainbutler.domain.bugreport.dto.BugReportUpdateRequest;
import com.rainbutler.domain.bugreport.entity.BugReportCategory;
import com.rainbutler.domain.bugreport.entity.BugReportStatus;
import com.rainbutler.domain.bugreport.service.BugReportAdminService;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.response.ApiResponse;
import com.rainbutler.global.response.PageResponse;
import com.rainbutler.global.security.LoginUserId;
import com.rainbutler.global.swagger.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 버그 신고 관리자 API입니다. **ADMIN만** 쓸 수 있습니다. (CITY_OFFICIAL은 403)
 */
@Tag(name = "Admin", description = "관리자")
@RestController
@RequestMapping("/api/v1/admin/bug-reports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class BugReportAdminController {

    private final BugReportAdminService bugReportAdminService;

    /**
     * 버그 신고 목록을 조회합니다.
     */
    @Operation(summary = "버그 신고 목록 (관리자)", description = "버그 신고를 최신순으로 조회합니다. "
            + "`status` · `category`로 거를 수 있습니다 (선택). `content`는 앞 100자 미리보기입니다. "
            + "**ADMIN만** (CITY_OFFICIAL은 403)")
    @ApiErrorCodes({ErrorCode.INVALID_INPUT, ErrorCode.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN,
            ErrorCode.INVALID_TOKEN, ErrorCode.FORBIDDEN})
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminBugReportResponse>>> search(
            @RequestParam(required = false) BugReportStatus status,
            @RequestParam(required = false) BugReportCategory category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                bugReportAdminService.search(status, category, page, size)));
    }

    /**
     * 버그 신고 상세를 조회합니다.
     */
    @Operation(summary = "버그 신고 상세 (관리자)", description = "내용 전체, 화면 · requestId · 앱 버전 · "
            + "플랫폼 · User-Agent, 스크린샷 URL, 관리자 메모, 처리자를 조회합니다. `requestId`로 Grafana "
            + "로그를 찾을 수 있습니다. **ADMIN만**")
    @ApiErrorCodes({ErrorCode.BUG_REPORT_NOT_FOUND, ErrorCode.UNAUTHORIZED,
            ErrorCode.EXPIRED_TOKEN, ErrorCode.INVALID_TOKEN, ErrorCode.FORBIDDEN})
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminBugReportDetailResponse>> find(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(bugReportAdminService.find(id)));
    }

    /**
     * 버그 신고 상태 · 관리자 메모를 바꿉니다.
     */
    @Operation(summary = "버그 신고 처리 (관리자)", description = "`status` · `adminNote`를 바꿉니다. "
            + "둘 다 선택이고 보낸 값만 바꿉니다. RESOLVED · WONT_FIX로 바꾸면 처리자 · 처리 시각을 남기고, "
            + "RECEIVED · IN_PROGRESS로 다시 열면 지웁니다. **ADMIN만**")
    @ApiErrorCodes({ErrorCode.INVALID_INPUT, ErrorCode.BUG_REPORT_NOT_FOUND,
            ErrorCode.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN, ErrorCode.INVALID_TOKEN,
            ErrorCode.FORBIDDEN})
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminBugReportDetailResponse>> update(
            @PathVariable Long id,
            @LoginUserId Long adminId,
            @Valid @RequestBody BugReportUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                bugReportAdminService.update(id, adminId, request)));
    }
}
