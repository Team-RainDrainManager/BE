package com.rainbutler.domain.push.service;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import com.google.firebase.messaging.WebpushConfig;
import com.google.firebase.messaging.WebpushNotification;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * FirebaseMessaging 발송 래퍼입니다. Firebase가 설정되지 않았으면 아무것도 보내지 않습니다.
 */
@Slf4j
@Component
public class FcmSender {

    /** sendEachForMulticast 한 번에 보낼 수 있는 최대 토큰 수 */
    private static final int MAX_TOKENS_PER_REQUEST = 500;

    /** 이 오류가 나면 토큰이 더 이상 유효하지 않으므로 지운다 */
    private static final Set<MessagingErrorCode> INVALID_TOKEN_ERRORS =
            Set.of(MessagingErrorCode.UNREGISTERED, MessagingErrorCode.SENDER_ID_MISMATCH);

    private final FirebaseMessaging firebaseMessaging;

    public FcmSender(ObjectProvider<FirebaseMessaging> firebaseMessaging) {
        this.firebaseMessaging = firebaseMessaging.getIfAvailable();
    }

    /**
     * 발송 결과입니다.
     *
     * @param successCount 성공한 기기 수
     * @param failureCount 실패한 기기 수
     * @param invalidTokens 무효라서 지워야 하는 토큰
     */
    public record SendResult(int successCount, int failureCount, List<String> invalidTokens) {

        static SendResult empty() {
            return new SendResult(0, 0, List.of());
        }
    }

    /**
     * Firebase가 설정돼 발송할 수 있는지 확인합니다.
     *
     * @return 발송 가능 여부
     */
    public boolean isEnabled() {
        return firebaseMessaging != null;
    }

    /**
     * 여러 기기에 같은 알림을 보냅니다. 웹(PWA)을 위해 WebpushConfig에도 같은 알림을 넣습니다.
     *
     * @param tokens 받을 기기 토큰
     * @param title 알림 제목
     * @param body 알림 내용
     * @param data 앱으로 함께 보낼 데이터
     * @return 성공 · 실패 수와 무효 토큰
     * @throws FirebaseMessagingException 요청 전체가 실패한 경우 (인증 오류 등)
     */
    public SendResult send(List<String> tokens, String title, String body, Map<String, String> data)
            throws FirebaseMessagingException {
        if (!isEnabled() || tokens.isEmpty()) {
            return SendResult.empty();
        }
        int success = 0;
        int failure = 0;
        List<String> invalidTokens = new ArrayList<>();
        for (int from = 0; from < tokens.size(); from += MAX_TOKENS_PER_REQUEST) {
            List<String> chunk =
                    tokens.subList(from, Math.min(from + MAX_TOKENS_PER_REQUEST, tokens.size()));
            BatchResponse response =
                    firebaseMessaging.sendEachForMulticast(message(chunk, title, body, data));
            success += response.getSuccessCount();
            failure += response.getFailureCount();
            collectInvalidTokens(chunk, response.getResponses(), invalidTokens);
        }
        return new SendResult(success, failure, invalidTokens);
    }

    /**
     * 알림 메시지를 만듭니다.
     *
     * <p>firebase-admin 9.11부터 등록 토큰 방식(addAllTokens · setToken)이 FID(Firebase 설치 ID) 방식으로
     * 대체 예정이라 deprecated입니다. 아직 정상 동작하고 프론트 SDK의 getToken() 값이 등록 토큰이므로 그대로
     * 쓰고, FID 전환은 프론트와 함께 따로 진행합니다.
     */
    @SuppressWarnings("deprecation")
    private static MulticastMessage message(
            List<String> tokens, String title, String body, Map<String, String> data) {
        return MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .setWebpushConfig(WebpushConfig.builder()
                        .setNotification(WebpushNotification.builder()
                                .setTitle(title)
                                .setBody(body)
                                .build())
                        .build())
                .putAllData(data)
                .build();
    }

    /**
     * 응답 순서는 토큰 순서와 같다. 무효 토큰은 모으고, 그 밖의 실패는 로그만 남긴다.
     */
    private static void collectInvalidTokens(
            List<String> tokens, List<SendResponse> responses, List<String> invalidTokens) {
        for (int i = 0; i < responses.size(); i++) {
            SendResponse response = responses.get(i);
            if (response.isSuccessful()) {
                continue;
            }
            MessagingErrorCode errorCode = response.getException() == null
                    ? null : response.getException().getMessagingErrorCode();
            if (INVALID_TOKEN_ERRORS.contains(errorCode)) {
                invalidTokens.add(tokens.get(i));
            } else {
                String message = response.getException() == null
                        ? "" : response.getException().getMessage();
                log.warn("[FCM 발송 실패] code={} {}", errorCode, message);
            }
        }
    }
}
