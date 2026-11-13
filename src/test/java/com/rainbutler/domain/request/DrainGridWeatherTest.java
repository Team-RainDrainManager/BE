package com.rainbutler.domain.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rainbutler.domain.drain.service.DrainWeatherAreaService;
import com.rainbutler.domain.drain.service.DrainWeatherAreaService.BackfillResult;
import com.rainbutler.domain.forecast.service.GridLocator;
import com.rainbutler.domain.request.dto.RainNoticeResponse.State;
import com.rainbutler.domain.request.dto.RainRequestStatus;
import com.rainbutler.domain.request.job.RainNoticeJob;
import com.rainbutler.domain.request.service.InspectionRequestService;
import com.rainbutler.domain.request.service.RainNoticeService;
import com.rainbutler.domain.request.service.RainRequestLookup;
import com.rainbutler.domain.request.service.RequestSchedulePolicy;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.geo.KmaGridConverter.Grid;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 빗물받이별 날씨(기상청 격자 단위 예보 · 알림 묶기 · 배너 · 지도 상태 · 백필)를 검증합니다.
 */
class DrainGridWeatherTest extends RequestIntegrationTest {

    private static final LocalDateTime NOTICE = LocalDateTime.of(2029, 7, 2, 6, 0);
    private static final LocalDate TODAY = NOTICE.toLocalDate();
    private static final LocalDate TOMORROW = TODAY.plusDays(1);
    /** 다른 격자 (서울시청 근처) */
    private static final Grid SEOUL_GRID = new Grid(60, 127);
    private static final double SEOUL_LAT = 37.5636;
    private static final double SEOUL_LNG = 126.9800;

    @Autowired
    private InspectionRequestService inspectionRequestService;

    @Autowired
    private RequestSchedulePolicy schedulePolicy;

    @Autowired
    private RainNoticeJob rainNoticeJob;

    @Autowired
    private RainNoticeService rainNoticeService;

    @Autowired
    private RainRequestLookup rainRequestLookup;

    @Autowired
    private GridLocator gridLocator;

    @Autowired
    private DrainWeatherAreaService drainWeatherAreaService;

    @Test
    void 격자_예보는_그_격자_빗물받이에만_요청을_만들고_격자_없는_빗물받이는_기본_격자로_본다() {
        now(NOTICE);
        Long owner = fixtures.user("집사");
        Long seoulAdopted = seoulDrain("명동");
        fixtures.adoption(owner, seoulAdopted, "빗물이");
        Long seoulOpen = seoulDrain("명동");
        // SQL로 넣은 빗물받이는 격자가 비어 있다 → 기본 격자(역곡)
        Long noGrid = fixtures.drain(RequestFixtures.BASE_LAT, RequestFixtures.BASE_LNG, false);
        Long seoulForecast = forecast(TOMORROW, SEOUL_GRID);
        Long defaultForecast = fixtures.forecast(TOMORROW, TOMORROW.atTime(15, 0), false);

        int seoulCreated = inspectionRequestService.createForForecast(
                seoulForecast, schedulePolicy.noticeSchedule(NOTICE));
        int defaultCreated = inspectionRequestService.createForForecast(
                defaultForecast, schedulePolicy.noticeSchedule(NOTICE));

        assertThat(seoulCreated).isEqualTo(2);
        assertThat(requestDrains(seoulForecast)).containsExactlyInAnyOrder(seoulAdopted, seoulOpen);
        assertThat(defaultCreated).isEqualTo(1);
        assertThat(requestDrains(defaultForecast)).containsExactly(noGrid);
        assertThat(fixtures.lastNotification(owner, "RAIN_TOMORROW")).isEqualTo(
                "내일 비 소식이 있어요 ☔ | 명동 '빗물이'를 오늘 20:00까지 살펴봐 줄 수 있나요? "
                        + "오늘 12:00까지 가능 여부를 알려 주세요.");
    }

    @Test
    void 같은_배치에서_한_사용자의_여러_격자_요청은_알림_1개로_묶는다() {
        now(NOTICE);
        Long owner = fixtures.user("집사");
        Long yeokgok = fixtures.drain(RequestFixtures.BASE_LAT, RequestFixtures.BASE_LNG, false);
        jdbcTemplate.update("UPDATE drain SET region_name = '역곡2동' WHERE id = ?", yeokgok);
        fixtures.adoption(owner, yeokgok, "또랑이");
        fixtures.adoption(owner, seoulDrain("명동"), "빗물이");
        Long other = fixtures.user("이웃");
        fixtures.adoption(other, seoulDrain("명동"), "이웃몽");
        fixtures.forecast(TOMORROW, TOMORROW.atTime(15, 0), false);
        forecast(TOMORROW, SEOUL_GRID);

        rainNoticeJob.run();

        assertThat(fixtures.notificationCount(owner, "RAIN_TOMORROW")).isEqualTo(1);
        assertThat(fixtures.lastNotification(owner, "RAIN_TOMORROW")).isEqualTo(
                "내일 비 소식: 또랑이 2곳 | 역곡2동 '또랑이', 명동 '빗물이' 2곳을 살펴봐 주세요. "
                        + "가장 이른 마감은 오늘 20:00까지예요.");
        // 다른 사용자는 따로 1곳 알림
        assertThat(fixtures.notificationCount(other, "RAIN_TOMORROW")).isEqualTo(1);
    }

    @Test
    void 배너는_좌표의_격자_기준이고_좌표가_없으면_기본_격자다() {
        now(NOTICE.withHour(10));
        forecast(TOMORROW, SEOUL_GRID);

        assertThat(rainNoticeService.current(gridLocator.locate(SEOUL_LAT, SEOUL_LNG)).state())
                .isEqualTo(State.FORECAST);
        assertThat(rainNoticeService.current(gridLocator.locate(null, null)).state())
                .isEqualTo(State.NONE);
        assertThatThrownBy(() -> gridLocator.locate(SEOUL_LAT, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 지도_상태는_예보와_관계없이_빗물받이의_진행_중_또는_오늘_완료_요청이다() {
        now(NOTICE.withHour(10));
        Long seoulForecast = forecast(TOMORROW, SEOUL_GRID);
        Long defaultForecast = fixtures.forecast(TOMORROW, TOMORROW.atTime(15, 0), false);
        Long seoul = seoulDrain("명동");
        Long yeokgok = fixtures.drain(RequestFixtures.BASE_LAT, RequestFixtures.BASE_LNG, false);
        Long doneToday = fixtures.drain(RequestFixtures.north(50), RequestFixtures.BASE_LNG, false);
        Long doneYesterday =
                fixtures.drain(RequestFixtures.north(100), RequestFixtures.BASE_LNG, false);
        LocalDateTime deadline = TODAY.atTime(20, 0);
        Long seoulRequest = fixtures.request(seoulForecast, seoul, null, "OPEN",
                TODAY.atTime(12, 0), deadline);
        Long yeokgokRequest = fixtures.request(defaultForecast, yeokgok, null, "CLAIMED",
                TODAY.atTime(12, 0), deadline);
        Long doneRequest = fixtures.request(defaultForecast, doneToday, null, "COMPLETED",
                TODAY.atTime(12, 0), deadline);
        jdbcTemplate.update("UPDATE inspection_request SET completed_at = ? WHERE id = ?",
                TODAY.atTime(9, 0), doneRequest);
        Long oldRequest = fixtures.request(defaultForecast, doneYesterday, null, "COMPLETED",
                TODAY.atTime(12, 0), deadline);
        jdbcTemplate.update("UPDATE inspection_request SET completed_at = ? WHERE id = ?",
                TODAY.minusDays(1).atTime(9, 0), oldRequest);

        var result = rainRequestLookup.findForDrains(
                List.of(seoul, yeokgok, doneToday, doneYesterday), TODAY);

        assertThat(result.get(seoul).requestId()).isEqualTo(seoulRequest);
        assertThat(result.get(seoul).status()).isEqualTo(RainRequestStatus.NEEDS_HELP);
        assertThat(result.get(yeokgok).requestId()).isEqualTo(yeokgokRequest);
        assertThat(result.get(yeokgok).status()).isEqualTo(RainRequestStatus.IN_PROGRESS);
        assertThat(result.get(doneToday).status()).isEqualTo(RainRequestStatus.DONE);
        assertThat(result).doesNotContainKey(doneYesterday);
    }

    @Test
    void 백필은_비어_있는_격자만_채우고_다시_돌려도_같다() {
        Long empty = fixtures.drain(SEOUL_LAT, SEOUL_LNG, false);
        Long filled = fixtures.drain(RequestFixtures.BASE_LAT, RequestFixtures.BASE_LNG, false);
        jdbcTemplate.update("UPDATE drain SET grid_nx = 1, grid_ny = 2 WHERE id = ?", filled);

        BackfillResult first = drainWeatherAreaService.backfill();
        BackfillResult second = drainWeatherAreaService.backfill();

        assertThat(first.gridFilled()).isPositive();
        assertThat(second.gridFilled()).isZero();
        assertThat(grid(empty)).isEqualTo(SEOUL_GRID);
        // 이미 있는 값은 건드리지 않는다
        assertThat(grid(filled)).isEqualTo(new Grid(1, 2));
        // 테스트에는 카카오 키가 없어 행정동 조회는 건너뛴다
        assertThat(first.regionFilled()).isZero();
    }

    /** 서울 격자의 빗물받이 (격자 · 행정동 이름을 채운다) */
    private Long seoulDrain(String regionName) {
        Long drain = fixtures.drain(SEOUL_LAT, SEOUL_LNG, false);
        jdbcTemplate.update("UPDATE drain SET grid_nx = ?, grid_ny = ?, region_name = ? "
                + "WHERE id = ?", SEOUL_GRID.nx(), SEOUL_GRID.ny(), regionName, drain);
        return drain;
    }

    private Long forecast(LocalDate date, Grid grid) {
        return fixtures.forecast(date, date.atTime(15, 0), false, grid);
    }

    private List<Long> requestDrains(Long forecast) {
        return jdbcTemplate.queryForList("SELECT drain_id FROM inspection_request "
                + "WHERE forecast_id = ?", Long.class, forecast);
    }

    private Grid grid(Long drain) {
        return jdbcTemplate.queryForObject("SELECT grid_nx, grid_ny FROM drain WHERE id = ?",
                (rs, n) -> new Grid(rs.getInt("grid_nx"), rs.getInt("grid_ny")), drain);
    }
}
