package com.rainbutler.domain.notification.repository;

import com.rainbutler.domain.notification.entity.Notification;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Notification 엔티티 저장소입니다. 쿼리 메서드는 도메인 담당자가 추가합니다.
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * 밤사이 알림함에만 쌓인 알림을 받는 사람과 함께 찾습니다. (야간 푸시 미동의자 09:00 푸시)
     *
     * <p>대상: 기간 안에 만들어졌고, 아직 푸시하지 않았고, 받는 사람이 알림 켬 + 야간 푸시 미동의.
     *
     * @param from 시작 (어제 야간 시작 시각, 포함)
     * @param to 끝 (오늘 야간 끝 시각, 제외)
     * @return 사용자 · 생성 순 알림
     */
    @Query("SELECT n FROM Notification n JOIN FETCH n.user u "
            + "WHERE n.createdAt >= :from AND n.createdAt < :to AND n.sentAt IS NULL "
            + "AND u.notificationEnabled = true AND u.nightPushConsent = false "
            + "ORDER BY u.id, n.createdAt")
    List<Notification> findDeferredNightNotifications(
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
