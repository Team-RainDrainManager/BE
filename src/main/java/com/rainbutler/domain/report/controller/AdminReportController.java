package com.rainbutler.domain.report.controller;

import com.rainbutler.domain.report.dto.ReportClearRequest;
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

    private static final String REVIEW = """
            제보를 처리합니다. ADMIN · CITY_OFFICIAL만 쓸 수 있고, 이미 처리된 제보는 다시 처리할 수 없습니다.

            | 처리 | 요청 body | 결과 |
            | --- | --- | --- |
            | 승인 | `{"approved": true, "adminComment": "확인했습니다"}` | APPROVED, 제보자 경험치 |
            | 반려 | `{"approved": false, "adminComment": "사유"}` | REJECTED (사유 필수) |
            | **제재** | `{"approved": false, "sanction": true, "adminComment": "사유"}` | REJECTED + 제재 |

            - **제재는 ADMIN만** (CITY_OFFICIAL은 403). 허위 제보로 보고 반려와 함께 제보자를 제재합니다.
            - 기간: 1회 7일 · 2회 14일 · 3회 이상 영구 (이미 제재 중이면 기존 종료 시각부터 이어짐).
              제재 중에는 점검 · 대신 점검을 할 수 없습니다 (제보 · 버그 신고는 가능).
            - **`adminComment`가 제재 사유로 사용자에게 그대로 보입니다.** 제재는 사유가 필수입니다.
            - `approved: true`와 `sanction: true`를 함께 보내면 400, 관리자 계정이 올린 제보는 제재할 수 없습니다(400).
            - 제보자가 탈퇴했으면 반려만 하고 제재는 하지 않습니다.
            - 막힘이 해결되면 제재가 아니라 `PATCH /api/v1/admin/reports/{reportId}/clear`(막힘 해제)를 씁니다.
            """;

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

    @Operation(summary = "제보 승인 · 반려 · 제재 (관리자)", description = REVIEW)
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

    @Operation(summary = "제보 입양 차단 해제 (관리자)", description = "승인된 막힘 제보의 입양 차단을 해제합니다. "
            + "해제하면 빗물받이를 다시 입양할 수 있고 지도에서 막힘 표시도 사라집니다. "
            + "승인 상태가 아닌 제보는 해제할 수 없습니다. ADMIN · CITY_OFFICIAL만 쓸 수 있습니다.")
    @ApiErrorCodes({ErrorCode.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN, ErrorCode.INVALID_TOKEN,
            ErrorCode.INVALID_INPUT, ErrorCode.USER_NOT_FOUND, ErrorCode.REPORT_NOT_FOUND,
            ErrorCode.REPORT_NOT_CLEARABLE, ErrorCode.FORBIDDEN})
    @PatchMapping("/{reportId}/clear")
    public ApiResponse<ReportDetailResponse> clear(
            @LoginUserId Long userId,
            @PathVariable Long reportId,
            @Valid @RequestBody(required = false) ReportClearRequest request
    ) {
        return ApiResponse.ok(reportService.clear(userId, reportId, request));
    }
}
