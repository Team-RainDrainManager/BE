package com.rainbutler.domain.push.event;

import java.util.Map;

/**
 * 알림 저장 후 푸시 발송을 요청하는 이벤트입니다. 트랜잭션 커밋 후 비동기로 처리됩니다.
 *
 * @param notificationId 저장된 알림 id
 * @param userId 받을 사용자 id
 * @param title 알림 제목
 * @param body 알림 내용
 * @param data 앱으로 함께 보낼 데이터 (type · notificationId 포함)
 */
public record PushRequestedEvent(
        Long notificationId, Long userId, String title, String body, Map<String, String> data) {
}
