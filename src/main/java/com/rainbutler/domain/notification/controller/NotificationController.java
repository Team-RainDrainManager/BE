package com.rainbutler.domain.notification.controller;

import com.rainbutler.domain.notification.dto.NotificationListResponse;
import com.rainbutler.domain.notification.dto.NotificationSummaryResponse;
import com.rainbutler.domain.notification.dto.ReadAllResponse;
import com.rainbutler.domain.notification.service.NotificationService;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.response.ApiResponse;
import com.rainbutler.global.security.LoginUserId;
import com.rainbutler.global.swagger.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 앱 알림함 API입니다.
 */
@Tag(name = "Notification", description = "알림함")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private static final String LIST = """
            내 알림을 최신순으로 조회합니다. (커서 페이지)

            - `unread=true`면 안 읽은 알림만. `unreadCount`는 필터와 관계없이 전체 안 읽은 수입니다.
            - `size` 기본 20, 최대 50 (넘으면 50).
            - 다음 페이지는 응답의 `nextCursor`를 `cursor`로 보냅니다. `nextCursor`가 null이면 마지막 페이지입니다.
            - 명세의 `text`는 `title` · `body`로 나눠 내려줍니다.

            **type별 이동 화면 (예시)**

            | type | 내용 | 이동 화면 |
            | --- | --- | --- |
            | `RAIN_TOMORROW` · `RAIN_TODAY` | 비 예보 점검 요청 | 요청 상세 (`requestId`) → 가능 · 불가 응답 / 점검 |
            | `SUBSTITUTE_OPEN` | 근처 대신 점검 공개 (**현재 미발송**, 이전 알림만) | 요청 상세 (`requestId`) |
            | `REQUEST_CLAIMED` | 내 요청을 이웃이 맡음 | 요청 상세 (`requestId`) |
            | `REQUEST_DEADLINE_CHANGED` | 비가 일찍 와서 마감 당겨짐 | 요청 상세 (`requestId`) |
            | `REQUEST_CANCELLED` | 예보 취소 · 비가 일찍 와서 취소 | 내 요청 목록 |
            | `REQUEST_MISSED` | 마감까지 미완료 (시청 인계) | 내 요청 목록 |
            | `MANAGEMENT_DUE` | 정기 관리 예정 | 내 빗물받이 (`adoptionId`) |
            | `ADOPTION_WARNING` · `ADOPTION_RELEASED` | 미관리 경고 · 자동 해제 | 내 빗물받이 (`adoptionId`) |
            | `REPORT_RESULT` | 제보 처리 결과 | 내 제보 |
            | `WELCOME` | 가입 환영 | 홈 |
            | `ADOPTION_REGISTERED` | 빗물받이 입양 완료 | 내 빗물받이 (`adoptionId`) |
            | `LEVEL_UP` | 빗물받이 레벨 상승 | 내 빗물받이 (`adoptionId`) |
            | `INSPECTION_APPROVED` | 점검 인증 · 검토 승인 | 내 점검 기록 |
            """;

    private final NotificationService notificationService;

    /**
     * 알림함 탭별 안 읽은 알림 수를 조회합니다.
     */
    @Operation(summary = "알림 탭별 안 읽은 수",
            description = "알림함의 점검 요청 · 활동 알림 탭 배지에 쓸 안 읽은 알림 수를 조회합니다.")
    @ApiErrorCodes({ErrorCode.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN, ErrorCode.INVALID_TOKEN})
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<NotificationSummaryResponse>> summary(
            @LoginUserId Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.summary(userId)));
    }

    /**
     * 내 알림 목록을 조회합니다.
     */
    @Operation(summary = "알림 목록", description = LIST)
    @ApiErrorCodes({ErrorCode.INVALID_INPUT, ErrorCode.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN,
            ErrorCode.INVALID_TOKEN})
    @GetMapping
    public ResponseEntity<ApiResponse<NotificationListResponse>> findMine(
            @LoginUserId Long userId,
            @RequestParam(defaultValue = "false") boolean unread,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(ApiResponse.ok(
                notificationService.findMine(userId, unread, cursor, size)));
    }

    /**
     * 알림 한 건을 읽음으로 표시합니다.
     */
    @Operation(summary = "알림 읽음", description = "내 알림 한 건을 읽음으로 표시합니다. 이미 읽은 알림도 "
            + "200입니다. 남의 알림이나 없는 알림은 404 `NOTIFICATION_NOT_FOUND`입니다.")
    @ApiErrorCodes({ErrorCode.NOTIFICATION_NOT_FOUND, ErrorCode.UNAUTHORIZED,
            ErrorCode.EXPIRED_TOKEN, ErrorCode.INVALID_TOKEN})
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(@LoginUserId Long userId,
            @PathVariable Long notificationId) {
        notificationService.markRead(userId, notificationId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /**
     * 안 읽은 알림을 모두 읽음으로 표시합니다.
     */
    @Operation(summary = "알림 모두 읽음",
            description = "내 안 읽은 알림을 모두 읽음으로 표시하고 바뀐 개수를 돌려줍니다.")
    @ApiErrorCodes({ErrorCode.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN, ErrorCode.INVALID_TOKEN})
    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<ReadAllResponse>> markAllRead(@LoginUserId Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.markAllRead(userId)));
    }
}
