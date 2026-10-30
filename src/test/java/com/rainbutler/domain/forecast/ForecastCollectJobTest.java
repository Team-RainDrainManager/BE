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
import com.rainbutler.domain.forecast.job.ForecastCollectJob;
import com.rainbutler.domain.forecast.repository.RainForecastRepository;
import com.rainbutler.support.IntegrationTestSupport;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 예보 수집 배치가 오늘 · 내일 예보만 반영하고, 실패 · 비활성 시 아무것도 바꾸지 않는지 검증합니다.
 */
class ForecastCollectJobTest extends IntegrationTestSupport {

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
        given(kmaForecastClient.fetchDailyRain(NOW, 60)).willReturn(List.of(
                rain(TODAY, 80, "10"), rain(TOMORROW, 30, "0"), rain(DAY_AFTER, 90, "30")));

        job.collect();

        assertThat(rainForecastRepository.findActive(TODAY, false)).isPresent();
        assertThat(rainForecastRepository.findActive(TOMORROW, false)).isEmpty();
        assertThat(rainForecastRepository.findActive(DAY_AFTER, false)).isEmpty();
    }

    @Test
    void 응답에_없는_날짜는_취소하지_않는다() {
        given(kmaForecastClient.fetchDailyRain(NOW, 60))
                .willReturn(List.of(rain(TOMORROW, 80, "10")));
        job.collect();

        given(kmaForecastClient.fetchDailyRain(NOW, 60))
                .willReturn(List.of(rain(TODAY, 80, "10")));
        job.collect();

        assertThat(rainForecastRepository.findActive(TOMORROW, false)).isPresent();
    }

    @Test
    void 기상청_호출이_실패하면_이번_회차는_아무것도_바꾸지_않는다() {
        given(kmaForecastClient.fetchDailyRain(NOW, 60))
                .willReturn(List.of(rain(TODAY, 80, "10")));
        job.collect();
        given(kmaForecastClient.fetchDailyRain(NOW, 60))
                .willThrow(new KmaApiException("기상청 오류"));

        job.collect();

        assertThat(rainForecastRepository.findActive(TODAY, false)).isPresent();
    }

    @Test
    void 스케줄러가_꺼진_테스트_프로필에서는_예약_실행이_아무것도_하지_않는다() {
        job.scheduledCollect();

        verify(kmaForecastClient, never()).fetchDailyRain(any(), anyInt());
    }

    private DailyRain rain(LocalDate date, int pop, String mm) {
        return new DailyRain(date, pop, new BigDecimal(mm), date.atTime(15, 0));
    }
}
