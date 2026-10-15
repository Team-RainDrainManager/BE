package com.rainbutler.domain.push.controller;

import com.rainbutler.domain.push.entity.FcmToken;
import com.rainbutler.domain.push.repository.FcmTokenRepository;
import com.rainbutler.domain.push.service.FcmSender;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.response.ApiResponse;
import com.rainbutler.global.security.LoginUserId;
import com.rainbutler.global.swagger.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 기기로 테스트 푸시를 보냅니다. (local · dev 프로필에서만 동작, 알림은 저장하지 않음)
 */
@Profile({"local", "dev"})
@Tag(name = "Push", description = "푸시 알림 (FCM 기기 토큰)")
@RestController
@RequestMapping("/api/v1/push")
@RequiredArgsConstructor
public class PushTestController {

    private final FcmTokenRepository fcmTokenRepository;
    private final FcmSender fcmSender;

    /**
     * 테스트 발송 결과입니다.
     *
     * @param firebaseEnabled Firebase 설정 여부 (false면 발송하지 않음)
     * @param tokenCount 내 기기 토큰 수
     * @param successCount 성공 수
     * @param failureCount 실패 수
     * @param invalidTokenCount 무효 토큰 수 (UNREGISTERED 등)
     */
    public record PushTestResponse(boolean firebaseEnabled, int tokenCount, int successCount,
            int failureCount, int invalidTokenCount) {
    }

    /**
     * 내 기기들로 테스트 알림을 보냅니다.
     */
    @Operation(summary = "[local · dev] 테스트 푸시 발송")
    @ApiErrorCodes({ErrorCode.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN, ErrorCode.INVALID_TOKEN})
    @PostMapping("/test")
    public ResponseEntity<ApiResponse<PushTestResponse>> sendTest(@LoginUserId Long userId)
            throws Exception {
        List<String> tokens = fcmTokenRepository.findAllByUser_Id(userId).stream()
                .map(FcmToken::getToken)
                .toList();
        FcmSender.SendResult result = fcmSender.send(tokens, "빗물받이 집사 테스트 알림",
                "푸시 알림이 정상적으로 도착했어요.", Map.of("type", "TEST"));
        return ResponseEntity.ok(ApiResponse.ok(new PushTestResponse(fcmSender.isEnabled(),
                tokens.size(), result.successCount(), result.failureCount(),
                result.invalidTokens().size())));
    }
}
