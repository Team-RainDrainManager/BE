package com.rainbutler.domain.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.rainbutler.domain.forecast.service.ForecastService;
import com.rainbutler.domain.request.job.RainNoticeJob;
import com.rainbutler.domain.request.service.ForecastSimulationService;
import com.rainbutler.domain.request.service.InspectionRequestService;
import com.rainbutler.domain.request.service.RequestSchedulePolicy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 비 예보 요청 생성 · 발송과 예보 취소 처리를 검증합니다.
 */
class InspectionRequestCreationTest extends RequestIntegrationTest {

    private static final LocalDateTime NOTICE = LocalDateTime.of(2027, 5, 10, 6, 0);
    private static final LocalDate TOMORROW = LocalDate.of(2027, 5, 11);

    @Autowired
    private InspectionRequestService inspectionRequestService;

    @Autowired
    private RequestSchedulePolicy schedulePolicy;

    @Autowired
    private RainNoticeJob rainNoticeJob;

    @Autowired
    private ForecastService forecastService;

    @Autowired
    private ForecastSimulationService forecastSimulationService;

    @Test
    void 입양된_빗물받이는_SENT로_만들고_입양자에게_알린다() {
        now(NOTICE);
        Long owner = fixtures.user("집사");
        Long drain = fixtures.drain(RequestFixtures.BASE_LAT, RequestFixtures.BASE_LNG, false);
        Long adoption = fixtures.adoption(owner, drain, "또랑이");
        Long forecast = fixtures.forecast(TOMORROW, TOMORROW.atTime(15, 0), false);

        int created = inspectionRequestService.createForForecast(
                forecast, schedulePolicy.noticeSchedule(NOTICE));

        assertThat(created).isEqualTo(1);
        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * FROM inspection_request "
                + "WHERE forecast_id = ?", forecast);
        assertThat(row.get("status")).isEqualTo("SENT");
        assertThat(row.get("adoption_id")).isEqualTo(adoption);
        assertThat(row.get("reply_deadline_at").toString()).startsWith("2027-05-10 12:00");
        assertThat(row.get("complete_deadline_at").toString()).startsWith("2027-05-10 20:00");
        assertThat(fixtures.lastNotification(owner, "RAIN_TOMORROW")).isEqualTo(
                "내일 비 소식이 있어요 ☔ | 또랑이를 오늘 20:00까지 살펴봐 주세요. "
                        + "오늘 12:00까지 가능 여부를 알려 주세요.");
    }

    @Test
    void 입양되지_않은_위험_빗물받이는_바로_OPEN이고_안전한_빗물받이는_만들지_않는다() {
        now(NOTICE);
        Long risky = fixtures.drain(RequestFixtures.BASE_LAT, RequestFixtures.BASE_LNG, true);
        fixtures.drain(RequestFixtures.north(50), RequestFixtures.BASE_LNG, false);
        Long neighbor = fixtures.user("이웃");
        fixtures.adoption(neighbor,
                fixtures.drain(RequestFixtures.north(100), RequestFixtures.BASE_LNG, false), "이웃몽");
        Long forecast = fixtures.forecast(TOMORROW, TOMORROW.atTime(15, 0), false);

        int created = inspectionRequestService.createForForecast(
                forecast, schedulePolicy.noticeSchedule(NOTICE));

        // 이웃 입양 빗물받이 1건(SENT) + 위험 빗물받이 1건(OPEN)
        assertThat(created).isEqualTo(2);
        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT status, adoption_id, "
                + "opened_at FROM inspection_request WHERE forecast_id = ? AND drain_id = ?",
                forecast, risky);
        assertThat(row.get("status")).isEqualTo("OPEN");
        assertThat(row.get("adoption_id")).isNull();
        assertThat(row.get("opened_at")).isNotNull();
        assertThat(fixtures.notificationCount(neighbor, "SUBSTITUTE_OPEN")).isEqualTo(1);
    }

    @Test
    void 쉬는_중인_입양자에게도_보낸다() {
        now(NOTICE);
        Long owner = fixtures.user("쉬는집사");
        jdbcTemplate.update("UPDATE users SET pause_until = ? WHERE id = ?",
                NOTICE.plusDays(5), owner);
        fixtures.adoption(owner,
                fixtures.drain(RequestFixtures.BASE_LAT, RequestFixtures.BASE_LNG, false), "쉬는몽");
        Long forecast = fixtures.forecast(TOMORROW, TOMORROW.atTime(15, 0), false);

        assertThat(inspectionRequestService.createForForecast(
                forecast, schedulePolicy.noticeSchedule(NOTICE))).isEqualTo(1);
    }

    @Test
    void 다른_예보의_진행_중_요청이_있거나_이미_만든_빗물받이는_건너뛴다() {
        now(NOTICE);
        Long owner = fixtures.user("집사");
        Long drain = fixtures.drain(RequestFixtures.BASE_LAT, RequestFixtures.BASE_LNG, false);
        Long adoption = fixtures.adoption(owner, drain, "또랑이");
        Long old = fixtures.forecast(NOTICE.toLocalDate(), NOTICE.withHour(18), false);
        fixtures.request(old, drain, adoption, "ACCEPTED", NOTICE.withHour(12),
                NOTICE.withHour(17));
        Long forecast = fixtures.forecast(TOMORROW, TOMORROW.atTime(15, 0), false);

        assertThat(inspectionRequestService.createForForecast(
                forecast, schedulePolicy.noticeSchedule(NOTICE))).isZero();

        jdbcTemplate.update("UPDATE inspection_request SET status = 'COMPLETED' "
                + "WHERE forecast_id = ?", old);
        assertThat(inspectionRequestService.createForForecast(
                forecast, schedulePolicy.noticeSchedule(NOTICE))).isEqualTo(1);
        assertThat(inspectionRequestService.createForForecast(
                forecast, schedulePolicy.noticeSchedule(NOTICE))).isZero();
    }

    @Test
    void 일괄_발송_배치는_내일_예보에_기본_일정으로_보낸다() {
        now(NOTICE);
        Long owner = fixtures.user("집사");
        fixtures.adoption(owner,
                fixtures.drain(RequestFixtures.BASE_LAT, RequestFixtures.BASE_LNG, false), "또랑이");
        Long forecast = fixtures.forecast(TOMORROW, TOMORROW.atTime(15, 0), false);

        assertThat(rainNoticeJob.run()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT complete_deadline_at FROM "
                + "inspection_request WHERE forecast_id = ?", String.class, forecast))
                .startsWith("2027-05-10 20:00");
    }

    @Test
    void 낮에_오늘_비가_확정되면_바로_보내고_06시_전_내일_비는_기다린다() {
        Long owner = fixtures.user("집사");
        fixtures.adoption(owner,
                fixtures.drain(RequestFixtures.BASE_LAT, RequestFixtures.BASE_LNG, false), "또랑이");

        now(LocalDateTime.of(2027, 5, 10, 5, 15));
        forecastService.apply(TOMORROW, 80, BigDecimal.TEN, TOMORROW.atTime(15, 0), false);
        assertThat(fixtures.notificationCount(owner, "RAIN_TOMORROW")).isZero();

        now(LocalDateTime.of(2027, 5, 10, 11, 15));
        forecastService.apply(LocalDate.of(2027, 5, 10), 80, BigDecimal.TEN,
                LocalDateTime.of(2027, 5, 10, 18, 0), false);
        assertThat(fixtures.lastNotification(owner, "RAIN_TODAY")).isEqualTo(
                "오늘 비 소식이 있어요 ☔ | 또랑이를 오늘 17:00까지 살펴봐 주세요. "
                        + "오늘 12:15까지 가능 여부를 알려 주세요.");
    }

    @Test
    void 예보가_취소되면_진행_중_요청만_취소하고_관련자에게_알린다() {
        now(NOTICE);
        Long owner = fixtures.user("집사");
        Long substitute = fixtures.user("대신집사");
        Long drain1 = fixtures.drain(RequestFixtures.BASE_LAT, RequestFixtures.BASE_LNG, false);
        Long adoption1 = fixtures.adoption(owner, drain1, "또랑이");
        Long drain2 = fixtures.drain(RequestFixtures.north(10), RequestFixtures.BASE_LNG, false);
        Long drain3 = fixtures.drain(RequestFixtures.north(20), RequestFixtures.BASE_LNG, false);
        Long forecast = fixtures.forecast(TOMORROW, TOMORROW.atTime(15, 0), false);
        Long sent = fixtures.request(forecast, drain1, adoption1, "SENT",
                NOTICE.withHour(12), NOTICE.withHour(20));
        Long claimed = fixtures.request(forecast, drain2, null, "OPEN",
                NOTICE.withHour(12), NOTICE.withHour(20));
        fixtures.claim(claimed, substitute);
        Long completed = fixtures.request(forecast, drain3, null, "COMPLETED",
                NOTICE.withHour(12), NOTICE.withHour(20));

        assertThat(inspectionRequestService.cancelForForecast(forecast)).isEqualTo(2);

        assertThat(fixtures.status(sent)).isEqualTo("CANCELLED");
        assertThat(fixtures.status(claimed)).isEqualTo("CANCELLED");
        assertThat(fixtures.status(completed)).isEqualTo("COMPLETED");
        assertThat(fixtures.notificationCount(owner, "REQUEST_CANCELLED")).isEqualTo(1);
        assertThat(fixtures.notificationCount(substitute, "REQUEST_CANCELLED")).isEqualTo(1);
    }

    @Test
    void 가상_예보_적용_결과에_생성_취소_요청_수가_들어간다() {
        now(LocalDateTime.of(2027, 5, 10, 10, 0));
        Long owner = fixtures.user("집사");
        fixtures.adoption(owner,
                fixtures.drain(RequestFixtures.BASE_LAT, RequestFixtures.BASE_LNG, false), "또랑이");
        fixtures.drain(RequestFixtures.north(30), RequestFixtures.BASE_LNG, true);

        var confirmed = forecastSimulationService.simulate(
                80, BigDecimal.TEN, LocalDateTime.of(2027, 5, 11, 15, 0));
        assertThat(confirmed.createdCount()).isEqualTo(2);
        assertThat(confirmed.cancelledCount()).isZero();

        var cancelled = forecastSimulationService.simulate(
                10, BigDecimal.ZERO, LocalDateTime.of(2027, 5, 11, 15, 0));
        assertThat(cancelled.createdCount()).isZero();
        assertThat(cancelled.cancelledCount()).isEqualTo(2);
    }
}
