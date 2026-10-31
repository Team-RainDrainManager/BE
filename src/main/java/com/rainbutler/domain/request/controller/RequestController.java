package com.rainbutler.domain.request.controller;

import com.rainbutler.domain.request.dto.MyRequestResponse;
import com.rainbutler.domain.request.dto.RequestClaimRequest;
import com.rainbutler.domain.request.dto.RequestClaimResponse;
import com.rainbutler.domain.request.dto.RequestReplyRequest;
import com.rainbutler.domain.request.dto.RequestReplyResponse;
import com.rainbutler.domain.request.service.MyRequestQueryService;
import com.rainbutler.domain.request.service.RequestReplyService;
import com.rainbutler.domain.request.service.SubstituteClaimService;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.response.ApiResponse;
import com.rainbutler.global.security.LoginUserId;
import com.rainbutler.global.swagger.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    private static final String CLAIM = """
            공개된(OPEN) 요청을 대신 점검으로 맡습니다. **누구나** 맡을 수 있고 입양권은 옮겨지지 않습니다.

            - 요청 바디의 위치는 앱을 쓰는 중의 현재 위치이며 **저장하지 않습니다**. 빗물받이에서 300m 안이어야 합니다.
            - 하루(00:00 기준) 최대 3건까지 맡을 수 있습니다. 맡은 뒤 취소 · 놓침이 된 것도 셉니다.
            - 선착순입니다. 동시에 맡으면 한 명만 성공하고 나머지는 409 `REQUEST_ALREADY_CLAIMED`.
            - 내 또랑이의 요청은 대신 점검으로 맡을 수 없습니다 (가능 · 불가 응답 API 사용).
            - 성공하면 원래 입양자에게 알림이 갑니다. 완료 마감(`completeDeadlineAt`)까지 점검해 주세요.
            """;

    private static final String MY_REQUESTS = """
            내 비 예보 요청 목록입니다. 진행 중인 요청을 마감이 임박한 순으로, 그다음 최근 24시간 안에 끝난(완료 · 놓침)
            요청을 보여 줍니다. 취소된 요청은 빠집니다.

            | role | 대상 | availableActions |
            | --- | --- | --- |
            | `OWNER` | 내 또랑이의 요청 | SENT → `REPLY`, `INSPECT` / ACCEPTED → `INSPECT` |
            | `CLAIMANT` | 내가 대신 점검으로 맡은 요청 | CLAIMED → `INSPECT` |
            | `NEARBY` | 대신 점검을 기다리는 요청 중 보낸 위치에서 300m 안 **또는** 내 또랑이에서 300m 안 | `CLAIM` |

            - `latitude` · `longitude`는 **선택**입니다 (앱 사용 중 위치, 저장하지 않음). 보내지 않으면 NEARBY는 내 또랑이
              근처만 나오고 `distanceM`은 null입니다. 둘 중 하나만 보내면 400.
            - `remainingMinutes`: 응답 전이면 응답 마감까지, 아니면 완료 마감까지 남은 분 (끝난 요청은 null)
            - `drain.name`: 입양된 빗물받이면 입양 이름, 아니면 null
            """;

    private final RequestReplyService requestReplyService;
    private final MyRequestQueryService myRequestQueryService;
    private final SubstituteClaimService substituteClaimService;

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

    /**
     * 대신 점검을 맡습니다.
     */
    @Operation(summary = "대신 점검 수락", description = CLAIM)
    @ApiErrorCodes({ErrorCode.INVALID_INPUT, ErrorCode.ONBOARDING_REQUIRED,
            ErrorCode.USER_SANCTIONED, ErrorCode.REQUEST_NOT_FOUND,
            ErrorCode.SUBSTITUTE_OWN_REQUEST, ErrorCode.SUBSTITUTE_OUT_OF_RANGE,
            ErrorCode.SUBSTITUTE_DAILY_LIMIT, ErrorCode.REQUEST_NOT_OPEN,
            ErrorCode.REQUEST_ALREADY_CLAIMED, ErrorCode.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN,
            ErrorCode.INVALID_TOKEN})
    @PostMapping("/{requestId}/claim")
    public ResponseEntity<ApiResponse<RequestClaimResponse>> claim(@LoginUserId Long userId,
            @PathVariable Long requestId, @Valid @RequestBody RequestClaimRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(substituteClaimService.claim(
                userId, requestId, request.latitude(), request.longitude())));
    }

    /**
     * 내 비 예보 요청 목록을 조회합니다.
     */
    @Operation(summary = "내 비 예보 요청 목록", description = MY_REQUESTS)
    @ApiErrorCodes({ErrorCode.INVALID_INPUT, ErrorCode.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN,
            ErrorCode.INVALID_TOKEN})
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<MyRequestResponse>>> findMine(
            @LoginUserId Long userId,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude) {
        if ((latitude == null) != (longitude == null)
                || (latitude != null && (Math.abs(latitude) > 90 || Math.abs(longitude) > 180))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "위치는 위도와 경도를 함께 보내 주세요.");
        }
        return ResponseEntity.ok(ApiResponse.ok(
                myRequestQueryService.findMine(userId, latitude, longitude)));
    }
}
