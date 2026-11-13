package com.rainbutler.domain.bugreport.controller;

import com.rainbutler.domain.bugreport.dto.BugReportCreateRequest;
import com.rainbutler.domain.bugreport.dto.BugReportCreateResponse;
import com.rainbutler.domain.bugreport.dto.MyBugReportResponse;
import com.rainbutler.domain.bugreport.service.BugReportService;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.response.ApiResponse;
import com.rainbutler.global.response.PageResponse;
import com.rainbutler.global.security.LoginUserId;
import com.rainbutler.global.swagger.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 앱 버그 신고 API입니다. (빗물받이 제보와는 별개)
 */
@Tag(name = "BugReport", description = "앱 버그 신고")
@RestController
@RequestMapping("/api/v1/bug-reports")
@RequiredArgsConstructor
public class BugReportController {

    private static final String CREATE = """
            앱(웹) 버그를 신고합니다. 운영팀이 확인합니다.

            - `category`: ERROR(오류 · 멈춤) · UI(화면 깨짐 · 표시 이상) · FEATURE(기능이 안 됨) ·
              PERFORMANCE(느림) · OTHER(기타)
            - `title`: 앞뒤 공백을 뺀 2~100자
            - `content`: 앞뒤 공백을 뺀 10~2000자
            - 선택: `screen`(버그가 난 화면 · 경로), `requestId`(**마지막으로 받은 응답 헤더 `X-Request-Id`** — 로그를
              바로 찾을 수 있어요), `appVersion`, `platform`(WEB · IOS · ANDROID)
            - 스크린샷 `photoKeys` 최대 3개: `POST /api/v1/uploads/presigned-url`에
              **용도 `BUG_REPORT`**로 올린 key
            - User-Agent는 서버가 요청 헤더에서 채웁니다
            - 하루 10건까지 (넘으면 429 `BUG_REPORT_DAILY_LIMIT`). 온보딩 전 · 제재 중이어도 신고할 수 있습니다
            """;

    private final BugReportService bugReportService;

    /**
     * 버그를 신고합니다.
     */
    @Operation(summary = "버그 신고", description = CREATE)
    @ApiErrorCodes({ErrorCode.INVALID_INPUT, ErrorCode.BUG_REPORT_DAILY_LIMIT,
            ErrorCode.INVALID_PHOTO_KEY, ErrorCode.PHOTO_FORBIDDEN, ErrorCode.PHOTO_NOT_UPLOADED,
            ErrorCode.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN, ErrorCode.INVALID_TOKEN,
            ErrorCode.USER_NOT_FOUND})
    @PostMapping
    public ResponseEntity<ApiResponse<BugReportCreateResponse>> create(
            @LoginUserId Long userId,
            @Valid @RequestBody BugReportCreateRequest request,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.USER_AGENT,
                    required = false) String userAgent) {
        return ResponseEntity.ok(ApiResponse.ok(
                bugReportService.create(userId, request, userAgent)));
    }

    /**
     * 내 신고 목록을 최신순으로 조회합니다.
     */
    @Operation(summary = "내 버그 신고 목록", description = "내가 신고한 버그를 최신순으로 조회합니다. "
            + "`title`은 전체, `content`는 앞 100자 미리보기입니다. "
            + "상태: RECEIVED 접수 · IN_PROGRESS 확인 중 · RESOLVED 해결 · WONT_FIX 보류")
    @ApiErrorCodes({ErrorCode.INVALID_INPUT, ErrorCode.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN,
            ErrorCode.INVALID_TOKEN})
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PageResponse<MyBugReportResponse>>> findMine(
            @LoginUserId Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(bugReportService.findMine(userId, page, size)));
    }
}
