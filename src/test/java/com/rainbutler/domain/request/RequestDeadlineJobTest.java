package com.rainbutler.domain.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.rainbutler.domain.request.job.RequestDeadlineJob;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 응답 · 완료 마감 처리 배치를 검증합니다.
 */
class RequestDeadlineJobTest extends RequestIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2027, 8, 1, 12, 0);

    @Autowired
    private RequestDeadlineJob job;

    private Long forecast;
    private Long owner;
    private Long drain;
    private Long adoption;

    @BeforeEach
    void setUp() {
        now(NOW);
        LocalDate tomorrow = NOW.toLocalDate().plusDays(1);
        forecast = fixtures.forecast(tomorrow, tomorrow.atTime(15, 0), false);
        owner = fixtures.user("집사");
        drain = fixtures.drain(RequestFixtures.BASE_LAT, RequestFixtures.BASE_LNG, false);
        adoption = fixtures.adoption(owner, drain, "또랑이");
    }

    @Test
    void 응답_마감까지_무응답이면_대신_점검으로_공개하고_무응답_횟수를_늘린다() {
        Long helper = fixtures.user("이웃");
        fixtures.adoption(helper,
                fixtures.drain(RequestFixtures.north(100), RequestFixtures.BASE_LNG, false), "이웃몽");
        Long request = fixtures.request(forecast, drain, adoption, "SENT", NOW,
                NOW.withHour(20));

        job.run();

        assertThat(fixtures.status(request)).isEqualTo("OPEN");
        assertThat(jdbcTemplate.queryForObject("SELECT ignore_count FROM adoption WHERE id = ?",
                Integer.class, adoption)).isEqualTo(1);
        assertThat(fixtures.notificationCount(helper, "SUBSTITUTE_OPEN")).isEqualTo(1);
    }

    @Test
    void 응답_마감_전이면_그대로_둔다() {
        Long request = fixtures.request(forecast, drain, adoption, "SENT", NOW.plusMinutes(1),
                NOW.withHour(20));

        job.run();

        assertThat(fixtures.status(request)).isEqualTo("SENT");
    }

    @Test
    void 완료_마감이_지나면_MISSED이고_입양자에게_알린다() {
        Long accepted = fixtures.request(forecast, drain, adoption, "ACCEPTED", NOW.minusHours(3),
                NOW);

        job.run();

        assertThat(fixtures.status(accepted)).isEqualTo("MISSED");
        assertThat(jdbcTemplate.queryForObject("SELECT missed_at FROM inspection_request "
                + "WHERE id = ?", LocalDateTime.class, accepted)).isEqualTo(NOW);
        assertThat(fixtures.lastNotification(owner, "REQUEST_MISSED"))
                .startsWith("이번 비 예보 점검은 시청에 전달됐어요");
    }

    @Test
    void 두_마감이_함께_지난_요청은_공개하지_않고_바로_MISSED다() {
        Long request = fixtures.request(forecast, drain, adoption, "SENT", NOW.minusHours(1),
                NOW.minusMinutes(5));

        job.run();

        assertThat(fixtures.status(request)).isEqualTo("MISSED");
        assertThat(jdbcTemplate.queryForObject("SELECT ignore_count FROM adoption WHERE id = ?",
                Integer.class, adoption)).isZero();
    }

    @Test
    void 완료_또는_취소된_요청은_건드리지_않는다() {
        Long completed = fixtures.request(forecast, drain, adoption, "COMPLETED",
                NOW.minusHours(3), NOW.minusHours(1));
        Long other = fixtures.drain(RequestFixtures.north(10), RequestFixtures.BASE_LNG, false);
        Long cancelled = fixtures.request(forecast, other, null, "CANCELLED", NOW.minusHours(3),
                NOW.minusHours(1));

        job.run();

        assertThat(fixtures.status(completed)).isEqualTo("COMPLETED");
        assertThat(fixtures.status(cancelled)).isEqualTo("CANCELLED");
    }

    @Test
    void 외로운_또랑이는_연속_무응답_3회부터다() {
        jdbcTemplate.update("UPDATE adoption SET ignore_count = 2 WHERE id = ?", adoption);
        fixtures.request(forecast, drain, adoption, "SENT", NOW, NOW.withHour(20));

        job.run();

        assertThat(jdbcTemplate.queryForObject("SELECT ignore_count FROM adoption WHERE id = ?",
                Integer.class, adoption)).isEqualTo(3);
    }
}
