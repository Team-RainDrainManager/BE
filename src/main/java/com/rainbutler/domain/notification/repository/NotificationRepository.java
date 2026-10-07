package com.rainbutler.domain.notification.repository;

import com.rainbutler.domain.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Notification 엔티티 저장소입니다. 쿼리 메서드는 도메인 담당자가 추가합니다.
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {
}
