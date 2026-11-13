package com.rainbutler.domain.request;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rainbutler.domain.weather.repository.CurrentWeatherRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 지도 비 예보 배너의 상태 우선순위 · 대상 예보 선택 · 진행 상황 · 문구를 검증합니다. (로그인 없이 호출)
 */
@AutoConfigureMockMvc
class RainNoticeApiTest extends RequestIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2028, 6, 1, 10, 0);
    private static final LocalDate TODAY = NOW.toLocalDate();
    private static final LocalDate TOMORROW = TODAY.plusDays(1);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CurrentWeatherRepository currentWeatherRepository;

    @BeforeEach
    void setUp() {
        now(NOW);
        jdbcTemplate.update("DELETE FROM current_weather");
    }

    @Test
    void 비도_예보도_없으면_NONE이다() throws Exception {
        notice()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("NONE"))
                .andExpect(jsonPath("$.data.raining").value(false))
                .andExpect(jsonPath("$.data.forecast").doesNotExist())
                .andExpect(jsonPath("$.data.help").doesNotExist())
                .andExpect(jsonPath("$.data.message").doesNotExist());
    }

    @Test
    void 내일_비_예보면_예보_마감_진행_상황_문구를_준다() throws Exception {
        Long forecast = fixtures.forecast(TOMORROW, TOMORROW.atTime(15, 0), false);
        request(forecast, "OPEN", NOW.withHour(20));
        request(forecast, "OPEN", NOW.withHour(20));
        request(forecast, "SENT", NOW.withHour(19));
        request(forecast, "CLAIMED", NOW.withHour(20));
        request(forecast, "COMPLETED", NOW.withHour(18));
        request(forecast, "EXPIRED", NOW.withHour(17));
        request(forecast, "MISSED", NOW.withHour(17));
        request(forecast, "CANCELLED", NOW.withHour(17));

        notice()
                .andExpect(jsonPath("$.data.state").value("FORECAST"))
                .andExpect(jsonPath("$.data.forecast.forecastId").value(forecast))
                .andExpect(jsonPath("$.data.forecast.rainDate").value(TOMORROW.toString()))
                .andExpect(jsonPath("$.data.forecast.rainStartAt").value("2028-06-02T15:00:00"))
                .andExpect(jsonPath("$.data.forecast.completeDeadlineAt")
                        .value("2028-06-01T19:00:00"))
                .andExpect(jsonPath("$.data.forecast.simulated").value(false))
                .andExpect(jsonPath("$.data.help.needsHelpCount").value(2))
                .andExpect(jsonPath("$.data.help.inProgressCount").value(2))
                .andExpect(jsonPath("$.data.help.doneCount").value(1))
                .andExpect(jsonPath("$.data.message")
                        .value("내일 15시쯤 비 예보가 있어요. 비 오기 전에 빗물받이를 살펴봐 주세요."));
    }

    @Test
    void 오늘_비면_오늘_문구이고_요청이_없으면_마감은_null이다() throws Exception {
        fixtures.forecast(TODAY, TODAY.atTime(18, 0), false);

        notice()
                .andExpect(jsonPath("$.data.forecast.completeDeadlineAt").doesNotExist())
                .andExpect(jsonPath("$.data.help.needsHelpCount").value(0))
                .andExpect(jsonPath("$.data.message")
                        .value("오늘 18시쯤 비 예보가 있어요. 비 오기 전에 빗물받이를 살펴봐 주세요."));
    }

    @Test
    void 같은_날짜면_실제_예보를_가까운_날짜면_가상_예보도_고른다() throws Exception {
        Long simulatedTomorrow = fixtures.forecast(TOMORROW, TOMORROW.atTime(9, 0), true);
        Long realTomorrow = fixtures.forecast(TOMORROW, TOMORROW.atTime(15, 0), false);
        notice().andExpect(jsonPath("$.data.forecast.forecastId").value(realTomorrow));

        jdbcTemplate.update("UPDATE rain_forecast SET status = 'CANCELLED' WHERE id = ?",
                realTomorrow);
        notice().andExpect(jsonPath("$.data.forecast.forecastId").value(simulatedTomorrow))
                .andExpect(jsonPath("$.data.forecast.simulated").value(true));

        Long simulatedToday = fixtures.forecast(TODAY, TODAY.atTime(18, 0), true);
        notice().andExpect(jsonPath("$.data.forecast.forecastId").value(simulatedToday));
    }

    @Test
    void 비가_오면_예보가_있어도_RAINING이고_문구는_비가_오고_있어요만이다() throws Exception {
        Long forecast = fixtures.forecast(TOMORROW, TOMORROW.atTime(15, 0), false);
        currentWeatherRepository.saveObservation(RequestFixtures.GRID,
                1, new BigDecimal("3.0"), true, NOW.withMinute(0), NOW);

        notice()
                .andExpect(jsonPath("$.data.state").value("RAINING"))
                .andExpect(jsonPath("$.data.raining").value(true))
                .andExpect(jsonPath("$.data.observedAt").value("2028-06-01T10:00:00"))
                .andExpect(jsonPath("$.data.message").value("비가 오고 있어요."))
                .andExpect(jsonPath("$.data.forecast.forecastId").value(forecast));
    }

    @Test
    void 시연용_강제_비도_RAINING이다() throws Exception {
        currentWeatherRepository.saveSimulatedRainingUntil(
                RequestFixtures.GRID, NOW.plusMinutes(30), NOW);

        notice().andExpect(jsonPath("$.data.state").value("RAINING"));
    }

    private void request(Long forecast, String status, LocalDateTime completeDeadline) {
        Long drain = fixtures.drain(RequestFixtures.north(Math.random() * 1000),
                RequestFixtures.BASE_LNG, false);
        fixtures.request(forecast, drain, null, status, NOW.withHour(9), completeDeadline);
    }

    private ResultActions notice() throws Exception {
        return mockMvc.perform(get("/api/v1/rain-notice"));
    }
}
