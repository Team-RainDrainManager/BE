package com.rainbutler.domain.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.rainbutler.domain.forecast.service.ForecastService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 비가 앞당겨지면 요청 마감을 당기는지 검증합니다. (예보 갱신 · 확정을 거쳐 이벤트로 실행)
 */
class DeadlineAdvanceTest extends RequestIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2027, 9, 1);

    @Autowired
    private ForecastService forecastService;

    private Long owner;
    private Long drain;
    private Long adoption;
    /** 기존 요청이 걸린 내일 예보 (이벤트 없이 직접 만든다) */
    private Long tomorrowForecast;

    @BeforeEach
    void setUp() {
        owner = fixtures.user("집사");
        drain = fixtures.drain(RequestFixtures.BASE_LAT, RequestFixtures.BASE_LNG, false);
        adoption = fixtures.adoption(owner, drain, "또랑이");
        LocalDate tomorrow = TODAY.plusDays(1);
        tomorrowForecast = fixtures.forecast(tomorrow, tomorrow.atTime(15, 0), false);
    }

    @Test
    void 비_시작이_앞당겨지면_완료_마감과_응답_마감을_당기고_알린다() {
        now(TODAY.atTime(8, 0));
        Long request = fixtures.request(tomorrowForecast, drain, adoption, "SENT",
                TODAY.atTime(13, 0), TODAY.atTime(14, 0));
        confirm(TODAY, TODAY.atTime(15, 0));

        confirm(TODAY, TODAY.atTime(13, 0));

        assertThat(deadline(request, "complete_deadline_at")).isEqualTo(TODAY.atTime(12, 0));
        assertThat(deadline(request, "reply_deadline_at")).isEqualTo(TODAY.atTime(12, 0));
        assertThat(fixtures.lastNotification(owner, "REQUEST_DEADLINE_CHANGED")).isEqualTo(
                "점검 마감이 당겨졌어요 | 비가 예보보다 일찍 올 것 같아요. 오늘 12:00까지 살펴봐 주세요.");
    }

    @Test
    void 비_시작이_늦춰지면_마감은_그대로다() {
        now(TODAY.atTime(8, 0));
        Long request = fixtures.request(tomorrowForecast, drain, adoption, "ACCEPTED",
                TODAY.atTime(9, 0), TODAY.atTime(14, 0));
        confirm(TODAY, TODAY.atTime(15, 0));

        confirm(TODAY, TODAY.atTime(18, 0));

        assertThat(deadline(request, "complete_deadline_at")).isEqualTo(TODAY.atTime(14, 0));
        assertThat(fixtures.notificationCount(owner, "REQUEST_DEADLINE_CHANGED")).isZero();
    }

    @Test
    void 기본_흐름_요청은_오늘_저녁_비가_새로_확정되면_20시_마감이_당겨진다() {
        now(TODAY.atTime(10, 0));
        Long request = fixtures.request(tomorrowForecast, drain, adoption, "ACCEPTED",
                TODAY.atTime(12, 0), TODAY.atTime(20, 0));

        confirm(TODAY, TODAY.atTime(18, 0));

        assertThat(deadline(request, "complete_deadline_at")).isEqualTo(TODAY.atTime(17, 0));
        assertThat(deadline(request, "reply_deadline_at")).isEqualTo(TODAY.atTime(12, 0));
    }

    @Test
    void 당긴_마감까지_30분_이하면_요청을_취소한다() {
        now(TODAY.atTime(13, 40));
        Long request = fixtures.request(tomorrowForecast, drain, adoption, "ACCEPTED",
                TODAY.atTime(14, 0), TODAY.atTime(17, 0));
        confirm(TODAY, TODAY.atTime(18, 0));

        confirm(TODAY, TODAY.atTime(15, 10));

        assertThat(fixtures.status(request)).isEqualTo("CANCELLED");
        assertThat(fixtures.lastNotification(owner, "REQUEST_CANCELLED"))
                .startsWith("비가 일찍 와서 이번 점검은 쉬어요 | 비 올 때는 청소하지 마세요.");
    }

    @Test
    void 완료된_요청은_건드리지_않는다() {
        now(TODAY.atTime(8, 0));
        Long request = fixtures.request(tomorrowForecast, drain, adoption, "COMPLETED",
                TODAY.atTime(9, 0), TODAY.atTime(14, 0));
        confirm(TODAY, TODAY.atTime(15, 0));

        confirm(TODAY, TODAY.atTime(13, 0));

        assertThat(deadline(request, "complete_deadline_at")).isEqualTo(TODAY.atTime(14, 0));
        assertThat(fixtures.status(request)).isEqualTo("COMPLETED");
    }

    private void confirm(LocalDate date, LocalDateTime rainStartAt) {
        forecastService.apply(date, 80, BigDecimal.TEN, rainStartAt, false);
    }

    private LocalDateTime deadline(Long request, String column) {
        return jdbcTemplate.queryForObject("SELECT " + column + " FROM inspection_request "
                + "WHERE id = ?", LocalDateTime.class, request);
    }
}
