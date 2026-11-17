package com.rainbutler.domain.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.rainbutler.domain.forecast.service.ForecastService;
import com.rainbutler.domain.request.job.RainNoticeJob;
import com.rainbutler.domain.request.service.InspectionRequestService;
import com.rainbutler.domain.request.service.RequestSchedulePolicy;
import com.rainbutler.global.geo.KmaGridConverter.Grid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 같은 비에 이미 점검을 마친 빗물받이에는 예보가 다시 확정돼도 요청을 보내지 않는지 검증합니다.
 */
class RainRequestDedupTest extends RequestIntegrationTest {

    private static final LocalDateTime NOTICE = LocalDateTime.of(2030, 8, 5, 6, 0);
    private static final LocalDate TOMORROW = NOTICE.toLocalDate().plusDays(1);

    @Autowired
    private InspectionRequestService inspectionRequestService;

    @Autowired
    private RequestSchedulePolicy schedulePolicy;

    @Autowired
    private RainNoticeJob rainNoticeJob;

    @Autowired
    private ForecastService forecastService;

    @Test
    void 같은_비로_이미_완료했으면_취소_후_다시_확정된_예보로_요청하지_않는다() {
        now(NOTICE);
        Long owner = fixtures.user("집사");
        Long drain = fixtures.drain(RequestFixtures.BASE_LAT, RequestFixtures.BASE_LNG, false);
        Long adoption = fixtures.adoption(owner, drain, "또랑이");
        Long first = fixtures.forecast(TOMORROW, TOMORROW.atTime(15, 0), false);
        fixtures.request(first, drain, adoption, "COMPLETED", NOTICE.withHour(12),
                NOTICE.withHour(20));
        cancel(first);
        Long again = fixtures.forecast(TOMORROW, TOMORROW.atTime(15, 0), false);

        int created = inspectionRequestService.createForForecast(
                again, schedulePolicy.noticeSchedule(NOTICE));

        assertThat(created).isZero();
        assertThat(requestCount(again, drain)).isZero();
    }

    @Test
    void 완료하지_않고_취소된_요청은_다시_확정되면_다시_요청한다() {
        now(NOTICE);
        Long drain = fixtures.drain(RequestFixtures.BASE_LAT, RequestFixtures.BASE_LNG, false);
        Long first = fixtures.forecast(TOMORROW, TOMORROW.atTime(15, 0), false);
        fixtures.request(first, drain, null, "CANCELLED", NOTICE.withHour(12),
                NOTICE.withHour(20));
        cancel(first);
        Long again = fixtures.forecast(TOMORROW, TOMORROW.atTime(15, 0), false);

        inspectionRequestService.createForForecast(again, schedulePolicy.noticeSchedule(NOTICE));

        assertThat(requestCount(again, drain)).isEqualTo(1);
    }

    @Test
    void 다른_날짜_다른_격자_가상_예보의_완료_기록은_영향이_없다() {
        now(NOTICE);
        Long otherDay = fixtures.drain(RequestFixtures.BASE_LAT, RequestFixtures.BASE_LNG, false);
        Long otherGrid = fixtures.drain(RequestFixtures.north(50), RequestFixtures.BASE_LNG, false);
        Long simulatedDone =
                fixtures.drain(RequestFixtures.north(100), RequestFixtures.BASE_LNG, false);
        completed(fixtures.forecast(TOMORROW.minusDays(1), TOMORROW.minusDays(1).atTime(15, 0),
                false), otherDay);
        completed(fixtures.forecast(TOMORROW, TOMORROW.atTime(15, 0), false, new Grid(60, 127)),
                otherGrid);
        completed(fixtures.forecast(TOMORROW, TOMORROW.atTime(15, 0), true), simulatedDone);
        Long real = fixtures.forecast(TOMORROW, TOMORROW.atTime(15, 0), false);

        inspectionRequestService.createForForecast(real, schedulePolicy.noticeSchedule(NOTICE));

        assertThat(requestCount(real, otherDay)).isEqualTo(1);
        assertThat(requestCount(real, otherGrid)).isEqualTo(1);
        assertThat(requestCount(real, simulatedDone)).isEqualTo(1);
    }

    @Test
    void 아침_6시_배치에도_같은_규칙을_쓴다() {
        now(NOTICE);
        Long done = fixtures.drain(RequestFixtures.BASE_LAT, RequestFixtures.BASE_LNG, false);
        Long fresh = fixtures.drain(RequestFixtures.north(50), RequestFixtures.BASE_LNG, false);
        Long first = fixtures.forecast(TOMORROW, TOMORROW.atTime(15, 0), false);
        completed(first, done);
        cancel(first);
        Long again = fixtures.forecast(TOMORROW, TOMORROW.atTime(15, 0), false);

        rainNoticeJob.run();

        assertThat(requestCount(again, done)).isZero();
        assertThat(requestCount(again, fresh)).isEqualTo(1);
    }

    @Test
    void 즉시_발송에도_같은_규칙을_쓴다() {
        // 비 전날 낮에 확정되면 바로 보낸다
        now(NOTICE.withHour(11));
        Long done = fixtures.drain(RequestFixtures.BASE_LAT, RequestFixtures.BASE_LNG, false);
        Long first = fixtures.forecast(TOMORROW, TOMORROW.atTime(15, 0), false);
        completed(first, done);
        cancel(first);

        var result = forecastService.apply(RequestFixtures.GRID, TOMORROW, 80, BigDecimal.TEN,
                TOMORROW.atTime(15, 0), false);

        assertThat(requestCount(result.forecastId(), done)).isZero();
    }

    private void completed(Long forecast, Long drain) {
        fixtures.request(forecast, drain, null, "COMPLETED", NOTICE.withHour(12),
                NOTICE.withHour(20));
    }

    private void cancel(Long forecast) {
        jdbcTemplate.update("UPDATE rain_forecast SET status = 'CANCELLED' WHERE id = ?", forecast);
    }

    private int requestCount(Long forecast, Long drain) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM inspection_request "
                + "WHERE forecast_id = ? AND drain_id = ?", Integer.class, forecast, drain);
    }
}
