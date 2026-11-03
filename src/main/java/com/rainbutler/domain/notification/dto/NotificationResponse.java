package com.rainbutler.domain.notification.dto;

import com.rainbutler.domain.notification.entity.Notification;
import com.rainbutler.domain.notification.entity.NotificationCategory;
import com.rainbutler.domain.notification.entity.NotificationType;
import java.time.LocalDateTime;

/**
 * 알림 한 건입니다.
 *
 * @param notificationId 알림 id
 * @param type 알림 종류 (프론트가 이동할 화면을 정한다)
 * @param category 알림함 탭 구분 (점검 요청 · 활동 알림)
 * @param title 제목
 * @param body 내용
 * @param createdAt 만든 시각
 * @param read 읽었는지
 * @param requestId 관련 비 예보 요청 id (없으면 null)
 * @param adoptionId 관련 입양 id (없으면 null)
 */
public record NotificationResponse(
        Long notificationId,
        NotificationType type,
        NotificationCategory category,
        String title,
        String body,
        LocalDateTime createdAt,
        boolean read,
        Long requestId,
        Long adoptionId) {

    /**
     * 알림으로 응답을 만듭니다. 연관 엔티티는 id만 쓰므로 추가 조회가 없습니다.
     */
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                NotificationCategory.of(notification.getType()),
                notification.getTitle(),
                notification.getBody(),
                notification.getCreatedAt(),
                notification.getReadAt() != null,
                notification.getRequest() == null ? null : notification.getRequest().getId(),
                notification.getAdoption() == null ? null : notification.getAdoption().getId());
    }
}
