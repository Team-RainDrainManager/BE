package com.rainbutler.domain.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rainbutler.domain.forecast.client.KmaApiException;
import com.rainbutler.domain.user.entity.Role;
import com.rainbutler.domain.weather.client.KmaObservationClient;
import com.rainbutler.domain.weather.client.Observation;
import com.rainbutler.domain.weather.job.CurrentWeatherJob;
import com.rainbutler.domain.weather.repository.CurrentWeatherRepository;
import com.rainbutler.domain.weather.service.CurrentWeatherService;
import com.rainbutler.global.scheduling.SchedulingProperties;
import com.rainbutler.global.security.JwtTokenProvider;
import com.rainbutler.support.IntegrationTestSupport;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 현재 날씨 수집 · 비 판단 · 시연용 강제 비 API를 검증합니다.
 */
@AutoConfigureMockMvc
class CurrentWeatherTest extends IntegrationTestSupport {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2028, 5, 1, 15, 45);

    @Autowired
    private SchedulingProperties schedulingProperties;

    @Autowired
    private CurrentWeatherService currentWeatherService;

    @Autowired
    private CurrentWeatherRepository currentWeatherRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private Clock clock;

    /** 기상청 클라이언트는 스프링 빈을 갈아끼우지 않고 직접 목으로 만든다 (테스트 컨텍스트를 늘리지 않기 위해) */
    private final KmaObservationClient kmaObservationClient = mock(KmaObservationClient.class);
    private CurrentWeatherJob job;

    @BeforeEach
    void setUp() {
        job = new CurrentWeatherJob(
                kmaObservationClient, currentWeatherRepository, schedulingProperties, clock);
        given(clock.getZone()).willReturn(SEOUL);
        given(clock.instant()).willReturn(NOW.atZone(SEOUL).toInstant());
        jdbcTemplate.update("DELETE FROM current_weather");
    }

    @Test
    void 수집하면_1행에_저장하고_비_여부를_판단한다() {
        given(kmaObservationClient.fetch(NOW)).willReturn(Optional.of(
                new Observation(1, new BigDecimal("2.5"), NOW.withMinute(0))));

        job.collect();
        job.collect();

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM current_weather",
                Integer.class)).isEqualTo(1);
        var weather = currentWeatherRepository.find().orElseThrow();
        assertThat(weather.pty()).isEqualTo(1);
        assertThat(weather.rn1Mm()).isEqualByComparingTo("2.5");
        assertThat(weather.raining()).isTrue();
        assertThat(currentWeatherService.status(NOW).raining()).isTrue();
    }

    @Test
    void 기상청_호출이_실패하면_기존_값을_유지한다() {
        currentWeatherRepository.saveObservation(1, BigDecimal.ONE, true, NOW.withMinute(0), NOW);
        given(kmaObservationClient.fetch(any())).willThrow(new KmaApiException("기상청 오류"));

        job.collect();

        assertThat(currentWeatherRepository.find().orElseThrow().raining()).isTrue();
    }

    @Test
    void 실황이_3시간보다_오래되면_비가_아니다() {
        currentWeatherRepository.saveObservation(
                1, BigDecimal.ONE, true, NOW.minusHours(3).minusMinutes(1), NOW.minusHours(3));
        assertThat(currentWeatherService.status(NOW).raining()).isFalse();

        currentWeatherRepository.saveObservation(
                1, BigDecimal.ONE, true, NOW.minusHours(3), NOW.minusHours(3));
        assertThat(currentWeatherService.status(NOW).raining()).isTrue();
    }

    @Test
    void 아직_수집한_적이_없으면_비가_아니다() {
        assertThat(currentWeatherService.status(NOW).raining()).isFalse();
    }

    @Test
    void 스케줄러가_꺼진_테스트_프로필에서는_예약_실행이_아무것도_하지_않는다() {
        job.scheduledCollect();

        verify(kmaObservationClient, never()).fetch(any());
    }

    @Test
    void 관리자는_시연용으로_비_오는_중을_켜고_끈다() throws Exception {
        currentWeatherRepository.saveObservation(0, BigDecimal.ZERO, false, NOW.withMinute(0), NOW);

        simulate(Role.ADMIN, 60)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.raining").value(true))
                .andExpect(jsonPath("$.data.simulatedRainingUntil").value("2028-05-01T16:45:00"));
        assertThat(currentWeatherService.status(NOW.plusMinutes(59)).raining()).isTrue();
        assertThat(currentWeatherService.status(NOW.plusMinutes(60)).raining()).isFalse();
        // 관측 값은 그대로
        assertThat(currentWeatherRepository.find().orElseThrow().pty()).isZero();

        simulate(Role.ADMIN, 0)
                .andExpect(jsonPath("$.data.raining").value(false))
                .andExpect(jsonPath("$.data.simulatedRainingUntil").doesNotExist());
    }

    @Test
    void 시연용_강제_비는_ADMIN만_0에서_360분까지다() throws Exception {
        simulate(Role.CITY_OFFICIAL, 60).andExpect(status().isForbidden());
        simulate(Role.USER, 60).andExpect(status().isForbidden());
        simulate(Role.ADMIN, 361).andExpect(status().isBadRequest());
        simulate(Role.ADMIN, -1).andExpect(status().isBadRequest());
    }

    private ResultActions simulate(Role role, int minutes) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/weather/simulate-rain")
                .header("Authorization",
                        "Bearer " + jwtTokenProvider.createAccessToken(1L, role))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"minutes\":" + minutes + "}"));
    }
}
