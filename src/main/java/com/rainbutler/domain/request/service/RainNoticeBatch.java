package com.rainbutler.domain.request.service;

import com.rainbutler.domain.notification.entity.NotificationType;
import com.rainbutler.domain.push.service.PushService;
import com.rainbutler.global.util.KoreanJosa;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 한 번의 발송 실행(수집 배치 · 06:00 배치 · 예보 확정 처리) 안에서 같은 사용자에게 가는 비 예보 요청 알림을 모아
 * <b>1개</b>로 보냅니다.
 *
 * <p>요청을 만드는 쪽은 바로 보내지 않고 {@link #add}로 쌓고, {@link #run}으로 감싼 실행이 끝나면 사용자별로 묶어
 * 보냅니다. 요청 생성 트랜잭션이 커밋된 뒤 보내야 알림이 요청을 참조할 수 있으므로, 실행 끝에 사용자마다 새
 * 트랜잭션에서 보냅니다. 예보 확정 처리는 커밋 뒤 같은 스레드에서 이어 실행되므로 스레드별로 모읍니다.
 *
 * <ul>
 *   <li>1곳: {@code 내일 비 소식이 있어요 ☔} / {@code 역곡2동 '또랑이'를 내일 20:00까지 살펴봐 줄 수 있나요? ...}
 *       (동 이름이 없으면 기존 문구)</li>
 *   <li>2곳 이상: {@code 내일 비 소식: 또랑이 2곳} / {@code 역곡2동 '또랑이', 동탄2동 '빗물이' 2곳을 살펴봐
 *       주세요. ...} (4곳부터는 앞 2곳 + "외 N곳")</li>
 *   <li>오늘 · 내일 비가 섞이면 더 이른 오늘 쪽 종류 · 제목을 쓴다</li>
 * </ul>
 */
@Slf4j
@Component
public class RainNoticeBatch {

    /** 이름을 모두 적는 최대 곳 수. 넘으면 앞 {@link #SHOWN_WHEN_OVERFLOW}곳 + "외 N곳" */
    private static final int MAX_LISTED = 3;
    private static final int SHOWN_WHEN_OVERFLOW = 2;

    private final ThreadLocal<Map<Long, List<PendingNotice>>> pending = new ThreadLocal<>();
    private final PushService pushService;
    private final TransactionTemplate newTransaction;

    public RainNoticeBatch(PushService pushService, PlatformTransactionManager transactionManager) {
        this.pushService = pushService;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 보낼 비 예보 요청 알림 한 건입니다.
     *
     * @param userId 입양자 id
     * @param requestId 요청 id
     * @param adoptionId 입양 id
     * @param nickname 또랑이 별명
     * @param regionName 행정동 이름 (없으면 null)
     * @param completeDeadlineAt 완료 마감
     * @param replyDeadlineAt 응답 마감
     * @param today 발송 기준 날짜
     * @param tomorrow 내일 비면 true
     */
    public record PendingNotice(Long userId, Long requestId, Long adoptionId, String nickname,
            String regionName, LocalDateTime completeDeadlineAt, LocalDateTime replyDeadlineAt,
            LocalDate today, boolean tomorrow) {
    }

    /**
     * 실행 동안 알림을 모았다가 끝나면 사용자별로 묶어 보냅니다. 이미 모으는 중이면(바깥 실행 안) 그대로 실행만 합니다.
     *
     * @param work 실행할 일
     * @return 실행 결과
     */
    public <T> T run(Supplier<T> work) {
        if (pending.get() != null) {
            return work.get();
        }
        pending.set(new LinkedHashMap<>());
        try {
            return work.get();
        } finally {
            Map<Long, List<PendingNotice>> collected = pending.get();
            // 스케줄러 스레드는 다시 쓰이므로 보내기 전에 반드시 비운다
            pending.remove();
            flush(collected);
        }
    }

    /**
     * 실행 동안 알림을 모았다가 끝나면 보냅니다.
     *
     * @param work 실행할 일
     */
    public void run(Runnable work) {
        run(() -> {
            work.run();
            return null;
        });
    }

    /**
     * 알림을 쌓습니다. 모으는 중이 아니면 바로 1곳 알림으로 보냅니다. (호출하는 쪽 트랜잭션 안)
     *
     * @param notice 알림
     */
    public void add(PendingNotice notice) {
        Map<Long, List<PendingNotice>> collected = pending.get();
        if (collected == null) {
            send(List.of(notice));
            return;
        }
        collected.computeIfAbsent(notice.userId(), id -> new ArrayList<>()).add(notice);
    }

    private void flush(Map<Long, List<PendingNotice>> collected) {
        for (List<PendingNotice> notices : collected.values()) {
            try {
                newTransaction.executeWithoutResult(status -> send(notices));
            } catch (RuntimeException e) {
                // 한 사용자 실패가 다른 사용자 발송을 막지 않게 한다
                log.error("[비 예보 알림] userId={} 요청 {}건 발송 실패: {}", notices.get(0).userId(),
                        notices.size(), e.getMessage(), e);
            }
        }
    }

    private void send(List<PendingNotice> notices) {
        List<PendingNotice> sorted = notices.stream()
                .sorted(Comparator.comparing(PendingNotice::completeDeadlineAt)
                        .thenComparing(PendingNotice::requestId))
                .toList();
        PendingNotice first = sorted.get(0);
        boolean tomorrow = sorted.stream().allMatch(PendingNotice::tomorrow);
        NotificationType type =
                tomorrow ? NotificationType.RAIN_TOMORROW : NotificationType.RAIN_TODAY;
        String day = tomorrow ? "내일" : "오늘";
        Map<String, String> data = Map.of(
                "requestId", String.valueOf(first.requestId()),
                "requestIds", sorted.stream().map(n -> String.valueOf(n.requestId()))
                        .collect(Collectors.joining(",")));
        String title;
        String body;
        if (sorted.size() == 1) {
            title = day + " 비 소식이 있어요 ☔";
            body = singleBody(first);
        } else {
            title = day + " 비 소식: 또랑이 " + sorted.size() + "곳";
            body = multiBody(sorted, first);
        }
        pushService.notify(first.userId(), type, title, body, first.requestId(),
                first.adoptionId(), data);
    }

    private static String singleBody(PendingNotice notice) {
        String complete = DeadlineText.of(notice.completeDeadlineAt(), notice.today());
        String reply = DeadlineText.of(notice.replyDeadlineAt(), notice.today());
        if (notice.regionName() == null) {
            return KoreanJosa.eulReul(notice.nickname()) + " " + complete + "까지 살펴봐 주세요. "
                    + reply + "까지 가능 여부를 알려 주세요.";
        }
        return place(notice) + josaEulReul(notice.nickname()) + " " + complete
                + "까지 살펴봐 줄 수 있나요? " + reply + "까지 가능 여부를 알려 주세요.";
    }

    private static String multiBody(List<PendingNotice> sorted, PendingNotice first) {
        int total = sorted.size();
        boolean overflow = total > MAX_LISTED;
        String names = sorted.stream()
                .limit(overflow ? SHOWN_WHEN_OVERFLOW : total)
                .map(RainNoticeBatch::place)
                .collect(Collectors.joining(", "));
        String count = overflow ? " 외 " + (total - SHOWN_WHEN_OVERFLOW) + "곳" : " " + total + "곳";
        return names + count + "을 살펴봐 주세요. 가장 이른 마감은 "
                + DeadlineText.of(first.completeDeadlineAt(), first.today()) + "까지예요.";
    }

    /** 예: 역곡2동 '또랑이' (동 이름이 없으면 '또랑이') */
    private static String place(PendingNotice notice) {
        String name = "'" + notice.nickname() + "'";
        return notice.regionName() == null ? name : notice.regionName() + " " + name;
    }

    /** 따옴표로 감싼 이름 뒤에 붙일 을 / 를 */
    private static String josaEulReul(String nickname) {
        return KoreanJosa.eulReul(nickname).substring(nickname.length());
    }
}
