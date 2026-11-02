package com.rainbutler.domain.notification.service;

import com.rainbutler.domain.notification.dto.NotificationListResponse;
import com.rainbutler.domain.notification.dto.NotificationResponse;
import com.rainbutler.domain.notification.dto.ReadAllResponse;
import com.rainbutler.domain.notification.entity.Notification;
import com.rainbutler.domain.notification.repository.NotificationRepository;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 앱 알림함(알림 목록 · 읽음)을 처리합니다. 알림을 만들고 보내는 것은 {@code PushService}가 합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    /** 한 페이지 기본 개수 */
    public static final int DEFAULT_SIZE = 20;
    /** 한 페이지 최대 개수 (넘으면 이 값으로 줄인다) */
    public static final int MAX_SIZE = 50;

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    /**
     * 내 알림을 최신순으로 한 페이지 조회합니다.
     *
     * <p>size + 1개를 읽어 다음 페이지가 있는지 판단합니다. 다음 페이지가 있으면 이번 페이지 마지막 알림 id가
     * {@code nextCursor}가 됩니다.
     *
     * @param userId 로그인 사용자
     * @param unreadOnly true면 안 읽은 알림만
     * @param cursor 이전 페이지의 nextCursor (첫 페이지는 null)
     * @param size 페이지 크기 (null이면 20, 50 초과면 50)
     * @return 알림 목록 한 페이지
     * @throws BusinessException size · cursor가 1보다 작음 (INVALID_INPUT)
     */
    public NotificationListResponse findMine(Long userId, boolean unreadOnly, Long cursor,
            Integer size) {
        int pageSize = size == null ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        if (pageSize < 1 || (cursor != null && cursor < 1)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "size와 cursor는 1 이상이어야 해요.");
        }
        List<Notification> rows = notificationRepository.findPage(userId,
                cursor == null ? Long.MAX_VALUE : cursor, unreadOnly,
                PageRequest.of(0, pageSize + 1));
        boolean hasNext = rows.size() > pageSize;
        List<NotificationResponse> page = rows.stream()
                .limit(pageSize)
                .map(NotificationResponse::from)
                .toList();
        Long nextCursor = hasNext ? page.get(page.size() - 1).notificationId() : null;
        return new NotificationListResponse(
                notificationRepository.countByUser_IdAndReadAtIsNull(userId), nextCursor, page);
    }

    /**
     * 내 알림 한 건을 읽음으로 표시합니다. 이미 읽은 알림이어도 성공합니다 (멱등).
     *
     * @param userId 로그인 사용자
     * @param notificationId 알림 id
     * @throws BusinessException 없는 알림이거나 남의 알림 (404 NOTIFICATION_NOT_FOUND, 존재 여부를 숨긴다)
     */
    @Transactional
    public void markRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository
                .findByIdAndUser_Id(notificationId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
        notification.markRead(LocalDateTime.now(clock));
    }

    /**
     * 내 안 읽은 알림을 모두 읽음으로 표시합니다.
     *
     * @param userId 로그인 사용자
     * @return 바뀐 알림 수
     */
    @Transactional
    public ReadAllResponse markAllRead(Long userId) {
        return new ReadAllResponse(
                notificationRepository.markAllRead(userId, LocalDateTime.now(clock)));
    }
}
