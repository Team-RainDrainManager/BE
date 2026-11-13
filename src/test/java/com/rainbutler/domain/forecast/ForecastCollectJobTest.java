package com.rainbutler.domain.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.rainbutler.domain.forecast.client.DailyRain;
import com.rainbutler.domain.forecast.client.KmaApiException;
import com.rainbutler.domain.forecast.client.KmaForecastClient;
import com.rainbutler.domain.forecast.entity.RainForecast;
import com.rainbutler.domain.forecast.job.ForecastCollectJob;
import com.rainbutler.domain.forecast.repository.RainForecastRepository;
import com.rainbutler.global.geo.KmaGridConverter.Grid;
import com.rainbutler.support.IntegrationTestSupport;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 예보 수집 배치가 오늘 · 내일 예보만 반영하고, 실패 · 비활성 시 아무것도 바꾸지 않는지 검증합니다.
 */
@ExtendWith(OutputCaptureExtension.class)
class ForecastCollectJobTest extends IntegrationTestSupport {

    /** 기본 격자 (역곡동) */
    private static final Grid GRID = new Grid(57, 125);
    /** 다른 격자 (서울시청 근처) */
    private static final Grid SEOUL_GRID = new Grid(60, 127);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2031, 3, 10, 5, 15);
    private static final LocalDate TODAY = NOW.toLocalDate();
    private static final LocalDate TOMORROW = TODAY.plusDays(1);
    private static final LocalDate DAY_AFTER = TODAY.plusDays(2);

    @Autowired
    private ForecastCollectJob job;

    @Autowired
    private RainForecastRepository rainForecastRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private KmaForecastClient kmaForecastClient;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void setUp() {
        given(clock.getZone()).willReturn(SEOUL);
        given(clock.instant()).willReturn(NOW.atZone(SEOUL).toInstant());
        jdbcTemplate.update("DELETE FROM rain_forecast WHERE rain_date BETWEEN ? AND ?",
                TODAY, DAY_AFTER);
    }

    @Test
    void 오늘과_내일_예보만_반영한다() {
        given(kmaForecastClient.fetchDailyRain(GRID, NOW, 60)).willReturn(List.of(
                rain(TODAY, 80, "10"), rain(TOMORROW, 30, "0"), rain(DAY_AFTER, 90, "30")));

        job.collect();

        assertThat(active(TODAY, GRID)).isPresent();
        assertThat(active(TOMORROW, GRID)).isEmpty();
        assertThat(active(DAY_AFTER, GRID)).isEmpty();
    }

    @Test
    void 응답에_없는_날짜는_취소하지_않고_ERROR_로그를_남긴다(CapturedOutput output) {
        given(kmaForecastClient.fetchDailyRain(GRID, NOW, 60))
                .willReturn(List.of(rain(TOMORROW, 80, "10")));
        job.collect();

        given(kmaForecastClient.fetchDailyRain(GRID, NOW, 60))
                .willReturn(List.of(rain(TODAY, 80, "10")));
        job.collect();

        assertThat(active(TOMORROW, GRID)).isPresent();
        assertThat(output.getOut())
                .contains("ERROR")
                .contains("[비 예보 수집 누락] 격자 57,125 " + TOMORROW)
                .contains("ForecastDataMissingException")
                .contains("받은 날짜 [" + TODAY + "]");
    }

    @Test
    void 기상청_호출이_실패하면_이번_회차는_아무것도_바꾸지_않는다() {
        given(kmaForecastClient.fetchDailyRain(GRID, NOW, 60))
                .willReturn(List.of(rain(TODAY, 80, "10")));
        job.collect();
        given(kmaForecastClient.fetchDailyRain(GRID, NOW, 60))
                .willThrow(new KmaApiException("기상청 오류"));

        job.collect();

        assertThat(active(TODAY, GRID)).isPresent();
    }

    @Test
    void 스케줄러가_꺼진_테스트_프로필에서는_예약_실행이_아무것도_하지_않는다() {
        job.scheduledCollect();

        verify(kmaForecastClient, never()).fetchDailyRain(any(), any(), anyInt());
    }

    @Test
    void 격자_하나가_실패해도_나머지_격자는_반영한다() {
        // 서울 격자에 운영 중 빗물받이가 있으면 수집 대상이 된다
        Long drain = jdbcTemplate.queryForObject("INSERT INTO drain (latitude, longitude, "
                + "photo_url, dirt_level, adoption_status, status, is_risk, grid_nx, grid_ny, "
                + "created_at, updated_at) VALUES (37.5636, 126.98, 'http://x', 1, 'AVAILABLE', "
                + "'ACTIVE', false, 60, 127, now(), now()) RETURNING id", Long.class);
        given(kmaForecastClient.fetchDailyRain(GRID, NOW, 60))
                .willThrow(new KmaApiException("기상청 오류"));
        given(kmaForecastClient.fetchDailyRain(SEOUL_GRID, NOW, 60))
                .willReturn(List.of(rain(TODAY, 80, "10")));

        job.collect();

        assertThat(active(TODAY, SEOUL_GRID)).isPresent();
        assertThat(active(TODAY, GRID)).isEmpty();
        jdbcTemplate.update("UPDATE drain SET status = 'REMOVED' WHERE id = ?", drain);
    }

    private Optional<RainForecast> active(LocalDate date, Grid grid) {
        return rainForecastRepository.findActive(date, grid.nx(), grid.ny(), false);
    }

    private DailyRain rain(LocalDate date, int pop, String mm) {
        return new DailyRain(date, pop, new BigDecimal(mm), date.atTime(15, 0));
    }
}
