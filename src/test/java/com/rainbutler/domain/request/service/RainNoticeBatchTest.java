package com.rainbutler.domain.request.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.rainbutler.domain.notification.entity.NotificationType;
import com.rainbutler.domain.push.service.PushService;
import com.rainbutler.domain.request.service.RainNoticeBatch.PendingNotice;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 비 예보 알림 묶기의 문구 · 데이터를 검증합니다.
 */
class RainNoticeBatchTest {

    private static final LocalDate TODAY = LocalDate.of(2029, 7, 2);

    private final PushService pushService = mock(PushService.class);
    private final RainNoticeBatch batch =
            new RainNoticeBatch(pushService, mock(PlatformTransactionManager.class));

    @Test
    void 모으는_중이_아니면_바로_1곳_알림을_보낸다() {
        batch.add(notice(1L, 10L, "또랑이", "역곡2동", 20, true));

        verify(pushService).notify(1L, NotificationType.RAIN_TOMORROW, "내일 비 소식이 있어요 ☔",
                "역곡2동 '또랑이'를 오늘 20:00까지 살펴봐 줄 수 있나요? "
                        + "오늘 12:00까지 가능 여부를 알려 주세요.",
                10L, 100L, Map.of("requestId", "10", "requestIds", "10"));
    }

    @Test
    void 동_이름이_없으면_기존_문구다() {
        batch.add(notice(1L, 10L, "빗물몽", null, 20, true));

        verify(pushService).notify(eq(1L), any(), anyString(),
                eq("빗물몽을 오늘 20:00까지 살펴봐 주세요. 오늘 12:00까지 가능 여부를 알려 주세요."),
                anyLong(), anyLong(), any());
    }

    @Test
    void 실행_안에서_같은_사용자_알림은_가장_이른_마감_요청으로_1개만_보낸다() {
        batch.run(() -> {
            batch.add(notice(1L, 11L, "빗물이", "명동", 20, true));
            batch.add(notice(1L, 10L, "또랑이", "역곡2동", 17, false));
            batch.add(notice(2L, 20L, "이웃몽", null, 20, true));
            // 실행이 끝나기 전에는 보내지 않는다
            verify(pushService, never()).notify(anyLong(), any(), anyString(), anyString(),
                    anyLong(), anyLong(), any());
        });

        // 오늘 비가 섞이면 오늘 종류 · 제목
        verify(pushService).notify(1L, NotificationType.RAIN_TODAY, "오늘 비 소식: 또랑이 2곳",
                "역곡2동 '또랑이', 명동 '빗물이' 2곳을 살펴봐 주세요. "
                        + "가장 이른 마감은 오늘 17:00까지예요.",
                10L, 100L, Map.of("requestId", "10", "requestIds", "10,11"));
        verify(pushService, times(1)).notify(eq(2L), any(), anyString(), anyString(),
                anyLong(), anyLong(), any());
    }

    @Test
    void 네_곳부터는_앞_두_곳과_외_N곳으로_줄인다() {
        batch.run(() -> {
            for (int i = 0; i < 4; i++) {
                batch.add(notice(1L, 10L + i, "또랑이" + i, "역곡2동", 17 + i, true));
            }
        });

        verify(pushService).notify(eq(1L), eq(NotificationType.RAIN_TOMORROW),
                eq("내일 비 소식: 또랑이 4곳"),
                eq("역곡2동 '또랑이0', 역곡2동 '또랑이1' 외 2곳을 살펴봐 주세요. "
                        + "가장 이른 마감은 오늘 17:00까지예요."),
                eq(10L), eq(100L), eq(Map.of("requestId", "10", "requestIds", "10,11,12,13")));
    }

    private static PendingNotice notice(Long userId, Long requestId, String nickname,
            String regionName, int deadlineHour, boolean tomorrow) {
        return new PendingNotice(userId, requestId, 100L, nickname, regionName,
                TODAY.atTime(deadlineHour, 0), TODAY.atTime(LocalTime.NOON),
                TODAY, tomorrow);
    }
}
