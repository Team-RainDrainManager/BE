package com.rainbutler.domain.activity.controller;

import com.rainbutler.domain.activity.dto.ActivityListResponse;
import com.rainbutler.domain.activity.service.ActivityQueryService;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.response.ApiResponse;
import com.rainbutler.global.security.LoginUserId;
import com.rainbutler.global.swagger.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 활동 내역 API입니다.
 */
@Tag(name = "Activity", description = "활동 내역")
@RestController
@RequestMapping("/api/v1/users/me/activities")
@RequiredArgsConstructor
public class ActivityController {

    private static final String LIST = """
            내 점검 · 제보 · 경험치 내역을 최신순으로 조회합니다. (커서 페이지)

            - `type`: `ALL`(기본) · `INSPECTION` · `REPORT` · `POINT`
            - `cursor`: 응답의 `nextCursor`를 그대로 보냅니다. 첫 페이지는 생략합니다.
            - `size`: 기본 20, 최대 50 (넘으면 50)
            - `amount`: 경험치(`POINT`) 행만 내려갑니다. 적립은 양수, 회수는 음수입니다.
            - `status`: 점검 · 제보 상태 이름입니다. (`POINT`면 null)
            """;

    private final ActivityQueryService activityQueryService;

    /**
     * 내 활동 내역을 조회합니다.
     */
    @Operation(summary = "내 활동 내역", description = LIST)
    @ApiErrorCodes({ErrorCode.INVALID_INPUT, ErrorCode.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN,
            ErrorCode.INVALID_TOKEN})
    @GetMapping
    public ApiResponse<ActivityListResponse> findMine(
            @LoginUserId Long userId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size
    ) {
        return ApiResponse.ok(activityQueryService.findMine(userId, type, cursor, size));
    }
}
