package com.rainbutler.domain.notification.repository;

import com.rainbutler.domain.notification.entity.Notification;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * 내 알림을 최신순(id 내림차순)으로 커서 이후부터 찾습니다.
     *
     * @param userId 사용자 id
     * @param cursor 이 id보다 작은 알림만 (첫 페이지는 Long.MAX_VALUE)
     * @param unreadOnly true면 안 읽은 알림만
     * @param pageable 가져올 개수
     * @return 알림 목록
     */
    @Query("SELECT n FROM Notification n WHERE n.user.id = :userId AND n.id < :cursor "
            + "AND (:unreadOnly = false OR n.readAt IS NULL) ORDER BY n.id DESC")
    List<Notification> findPage(@Param("userId") Long userId, @Param("cursor") Long cursor,
            @Param("unreadOnly") boolean unreadOnly, Pageable pageable);

    /**
     * 안 읽은 알림 수를 셉니다.
     *
     * @param userId 사용자 id
     * @return 안 읽은 알림 수
     */
    long countByUser_IdAndReadAtIsNull(Long userId);

    /**
     * 내 알림 한 건을 찾습니다. 남의 알림은 찾지 않습니다.
     *
     * @param id 알림 id
     * @param userId 사용자 id
     * @return 알림
     */
    Optional<Notification> findByIdAndUser_Id(Long id, Long userId);

    /**
     * 내 안 읽은 알림을 모두 읽음으로 바꿉니다. (벌크 UPDATE)
     *
     * @param userId 사용자 id
     * @param now 읽은 시각
     * @return 바뀐 알림 수
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Notification n SET n.readAt = :now, n.updatedAt = :now "
            + "WHERE n.user.id = :userId AND n.readAt IS NULL AND n.deletedAt IS NULL")
    int markAllRead(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
