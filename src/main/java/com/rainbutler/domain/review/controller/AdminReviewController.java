package com.rainbutler.domain.review.controller;

import com.rainbutler.domain.review.dto.ReviewDecisionRequest;
import com.rainbutler.domain.review.dto.ReviewDecisionResponse;
import com.rainbutler.domain.review.dto.ReviewListResponse;
import com.rainbutler.domain.review.service.AdminReviewQueryService;
import com.rainbutler.domain.review.service.AdminReviewService;
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
 * 관리자 사진 검토 API입니다.
 *
 * <p>위치 불일치 · 낮은 AI 확신도로 검토 대기({@code PENDING})가 된 점검 · 관리 기록 · 청소를 승인하거나
 * 반려합니다. {@code ADMIN} · {@code CITY_OFFICIAL}만 쓸 수 있습니다.
 */
@Tag(name = "Admin", description = "관리자")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/reviews")
public class AdminReviewController {

    private static final String LIST = """
            사진 검토 대기 목록을 최신순으로 조회합니다.

            - `type`: `INSPECTION`(점검 · 청소 기록) · `MANAGEMENT_LOG`(입양 후 관리 기록) ·
              `CLEANING`(입양 전 · 대신 점검 청소). 생략하면 전체를 모아 최신순으로 내려갑니다.
            - `limit`: 가져올 개수. 기본 20, 최대 100입니다.
            - 응답이 같은 모양이라 종류와 상관없이 사진을 보고 승인 · 반려할 수 있습니다. 그 종류에 없는 값은
              `null`입니다.
            - `reason`에는 검토가 필요한 이유가 들어갑니다.
            - `ADMIN` · `CITY_OFFICIAL`만 쓸 수 있습니다.
            """;

    private static final String DECIDE = """
            검토 대기 기록을 승인하거나 반려합니다.

            - `type`: `INSPECTION` · `MANAGEMENT_LOG` · `CLEANING` (하이픈 표기도 허용: `management-log`)
            - `decision`: `APPROVE` · `REJECT`
            - `reason`: 반려 사유 (반려 시 필수, 최대 500자)
            - 점검(`INSPECTION`)을 승인하면 관리 주기 · 빗물받이 최신 사진 · 경험치 · 연결된 점검 요청 완료까지
              한 번에 처리합니다.
            - 이미 처리된 검토는 다시 처리할 수 없습니다. (409 `REVIEW_ALREADY_PROCESSED`)
            - `ADMIN` · `CITY_OFFICIAL`만 쓸 수 있습니다.
            """;

    private final AdminReviewQueryService adminReviewQueryService;
    private final AdminReviewService adminReviewService;

    /**
     * 검토 대기 목록을 조회합니다.
     */
    @Operation(summary = "사진 검토 대기 목록 (관리자)", description = LIST)
    @ApiErrorCodes({ErrorCode.INVALID_INPUT, ErrorCode.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN,
            ErrorCode.INVALID_TOKEN, ErrorCode.FORBIDDEN})
    @GetMapping
    public ApiResponse<ReviewListResponse> findPending(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer limit
    ) {
        return ApiResponse.ok(adminReviewQueryService.findPending(type, limit));
    }

    /**
     * 검토 대기 기록을 승인 · 반려합니다.
     */
    @Operation(summary = "사진 검토 승인 · 반려 (관리자)", description = DECIDE)
    @ApiErrorCodes({ErrorCode.INVALID_INPUT, ErrorCode.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN,
            ErrorCode.INVALID_TOKEN, ErrorCode.FORBIDDEN, ErrorCode.USER_NOT_FOUND,
            ErrorCode.REVIEW_NOT_FOUND, ErrorCode.REVIEW_ALREADY_PROCESSED})
    @PatchMapping("/{type}/{id}")
    public ApiResponse<ReviewDecisionResponse> decide(
            @LoginUserId Long userId,
            @PathVariable String type,
            @PathVariable Long id,
            @Valid @RequestBody ReviewDecisionRequest request
    ) {
        return ApiResponse.ok(adminReviewService.decide(userId, type, id, request));
    }
}
