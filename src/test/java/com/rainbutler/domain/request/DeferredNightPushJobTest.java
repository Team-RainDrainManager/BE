package com.rainbutler.domain.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.rainbutler.domain.push.job.DeferredNightPushJob;
import com.rainbutler.domain.push.service.FcmSender;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 야간 푸시 미동의자에게 밤사이 알림을 09:00에 묶어 보내는지 검증합니다.
 */
class DeferredNightPushJobTest extends RequestIntegrationTest {

    private static final LocalDateTime NINE = LocalDateTime.of(2027, 10, 2, 9, 0);

    @Autowired
    private DeferredNightPushJob job;

    @MockitoBean
    private FcmSender fcmSender;

    @BeforeEach
    void setUp() throws Exception {
        now(NINE);
        given(fcmSender.isEnabled()).willReturn(true);
        given(fcmSender.send(anyList(), anyString(), anyString(), anyMap()))
                .willReturn(new FcmSender.SendResult(1, 0, List.of()));
    }

    @Test
    void 밤사이_미동의자_알림_1건은_그_알림_그대로_보낸다() throws Exception {
        Long user = userWithToken("미동의", false);
        Long n = notification(user, NINE.minusDays(1).withHour(23), "내일 비 소식이 있어요 ☔");

        assertThat(job.run()).isEqualTo(1);

        verify(fcmSender).send(eq(List.of("token-" + user)), eq("내일 비 소식이 있어요 ☔"),
                eq("본문"), anyMap());
        assertThat(sentAt(n)).isNotNull();
    }

    @Test
    void 여러_건은_사용자별_1회로_묶어_보낸다() throws Exception {
        Long user = userWithToken("미동의", false);
        Long first = notification(user, NINE.minusDays(1).withHour(22), "첫째");
        Long second = notification(user, NINE.withHour(6), "둘째");

        job.run();

        verify(fcmSender).send(eq(List.of("token-" + user)), eq("밤사이 알림 2건이 있어요"),
                eq("앱에서 확인해 주세요."), anyMap());
        assertThat(sentAt(first)).isNotNull();
        assertThat(sentAt(second)).isNotNull();
    }

    @Test
    void 동의자_알림_끈_사용자_이미_보낸_알림_밤이_아닌_알림은_제외한다() throws Exception {
        Long consent = userWithToken("동의", true);
        notification(consent, NINE.minusDays(1).withHour(23), "동의자");
        Long off = userWithToken("알림끔", false);
        jdbcTemplate.update("UPDATE users SET notification_enabled = false WHERE id = ?", off);
        notification(off, NINE.minusDays(1).withHour(23), "알림끔");
        Long user = userWithToken("미동의", false);
        Long sent = notification(user, NINE.minusDays(1).withHour(23), "보냄");
        jdbcTemplate.update("UPDATE notification SET sent_at = now() WHERE id = ?", sent);
        notification(user, NINE.minusDays(1).withHour(21), "밤 전");
        notification(user, NINE.withHour(8), "아침");

        assertThat(job.run()).isZero();

        verify(fcmSender, never()).send(anyList(), anyString(),
                anyString(), anyMap());
    }

    private Long userWithToken(String nickname, boolean nightConsent) {
        Long user = fixtures.user(nickname, true, nightConsent);
        jdbcTemplate.update("INSERT INTO fcm_token (user_id, token, platform, last_used_at, "
                + "created_at, updated_at) VALUES (?, ?, 'WEB', now(), now(), now())",
                user, "token-" + user);
        return user;
    }

    private Long notification(Long user, LocalDateTime createdAt, String title) {
        return jdbcTemplate.queryForObject("INSERT INTO notification (user_id, type, title, body, "
                + "created_at, updated_at) VALUES (?, 'RAIN_TOMORROW', ?, '본문', ?, ?) "
                + "RETURNING id", Long.class, user, title, Timestamp.valueOf(createdAt),
                Timestamp.valueOf(createdAt));
    }

    private Timestamp sentAt(Long notification) {
        return jdbcTemplate.queryForObject(
                "SELECT sent_at FROM notification WHERE id = ?", Timestamp.class, notification);
    }
}
