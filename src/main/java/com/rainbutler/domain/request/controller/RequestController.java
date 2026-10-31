package com.rainbutler.domain.request.controller;

import com.rainbutler.domain.request.dto.RequestReplyRequest;
import com.rainbutler.domain.request.dto.RequestReplyResponse;
import com.rainbutler.domain.request.service.RequestReplyService;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.response.ApiResponse;
import com.rainbutler.global.security.LoginUserId;
import com.rainbutler.global.swagger.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 비 예보 점검 요청 API입니다.
 */
@Tag(name = "Request", description = "비 예보 점검 요청")
@RestController
@RequestMapping("/api/v1/requests")
@RequiredArgsConstructor
public class RequestController {

    private static final String REPLY = """
            비 예보 점검 요청에 가능 · 불가로 응답합니다. **요청을 받은 입양자 본인**만, **응답 마감 전**에
            한 번 응답할 수 있습니다. (기본 응답 마감: 발송 당일 12:00, 당일 예보는 발송 + 1시간)

            - `ACCEPT` → `ACCEPTED`: 완료 마감(기본 20:00)까지 점검해 주세요.
            - `DECLINE` → `OPEN`: 대신 점검으로 공개되고 근처 입양자에게 알림이 갑니다. 불이익은 없습니다.
            - 응답하면 연속 무응답 횟수가 초기화됩니다.
            """;

    private final RequestReplyService requestReplyService;

    /**
     * 가능 · 불가로 응답합니다.
     */
    @Operation(summary = "비 예보 요청 가능 · 불가 응답", description = REPLY)
    @ApiErrorCodes({ErrorCode.INVALID_INPUT, ErrorCode.ONBOARDING_REQUIRED, ErrorCode.FORBIDDEN,
            ErrorCode.REQUEST_NOT_FOUND, ErrorCode.REQUEST_REPLY_NOT_ALLOWED,
            ErrorCode.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN, ErrorCode.INVALID_TOKEN})
    @PostMapping("/{requestId}/reply")
    public ResponseEntity<ApiResponse<RequestReplyResponse>> reply(@LoginUserId Long userId,
            @PathVariable Long requestId, @Valid @RequestBody RequestReplyRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                requestReplyService.reply(userId, requestId, request.reply())));
    }
}
