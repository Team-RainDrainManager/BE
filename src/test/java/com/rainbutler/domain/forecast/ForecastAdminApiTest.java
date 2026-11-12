package com.rainbutler.domain.forecast;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rainbutler.domain.request.RequestFixtures;
import com.rainbutler.domain.user.entity.Role;
import com.rainbutler.global.security.JwtTokenProvider;
import com.rainbutler.support.IntegrationTestSupport;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
 * 가상 예보 적용 API의 권한 · 검증 · 반영 결과를 검증합니다. (시각은 Clock으로 고정)
 */
@AutoConfigureMockMvc
class ForecastAdminApiTest extends IntegrationTestSupport {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2032, 6, 1, 10, 0);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void setUp() {
        // 다른 테스트가 남긴 입양 · 빗물받이가 있으면 요청이 만들어져 createdCount가 달라진다
        new RequestFixtures(jdbcTemplate).neutralize();
        given(clock.getZone()).willReturn(SEOUL);
        given(clock.instant()).willReturn(NOW.atZone(SEOUL).toInstant());
    }

    @Test
    void 관리자는_가상_예보를_확정하고_갱신하고_취소한다() throws Exception {
        simulate(Role.ADMIN, 80, "12.5", "2032-06-02T15:00:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applied").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.forecastId").isNumber())
                .andExpect(jsonPath("$.data.createdCount").value(0))
                .andExpect(jsonPath("$.data.cancelledCount").value(0));
        simulate(Role.ADMIN, 90, "20", "2032-06-02T14:00:00")
                .andExpect(jsonPath("$.data.applied").value("UPDATED"));
        simulate(Role.ADMIN, 20, "0", "2032-06-02T14:00:00")
                .andExpect(jsonPath("$.data.applied").value("CANCELLED"));
        simulate(Role.ADMIN, 20, "0", "2032-06-02T14:00:00")
                .andExpect(jsonPath("$.data.applied").value("IGNORED"))
                .andExpect(jsonPath("$.data.forecastId").doesNotExist());
    }

    @Test
    void 시청_관계자와_일반_사용자는_403이다() throws Exception {
        simulate(Role.CITY_OFFICIAL, 80, "12.5", "2032-06-03T15:00:00")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        simulate(Role.USER, 80, "12.5", "2032-06-03T15:00:00")
                .andExpect(status().isForbidden());
    }

    @Test
    void 비_시작이_3시간_안이면_400이다() throws Exception {
        simulate(Role.ADMIN, 80, "12.5", "2032-06-01T12:59:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
        simulate(Role.ADMIN, 80, "12.5", "2032-06-01T13:00:00")
                .andExpect(status().isOk());
    }

    @Test
    void 강수확률과_강수량_범위를_벗어나면_400이다() throws Exception {
        simulate(Role.ADMIN, 101, "12.5", "2032-06-04T15:00:00")
                .andExpect(status().isBadRequest());
        simulate(Role.ADMIN, 80, "500.1", "2032-06-04T15:00:00")
                .andExpect(status().isBadRequest());
        simulate(Role.ADMIN, -1, "12.5", "2032-06-04T15:00:00")
                .andExpect(status().isBadRequest());
    }

    @Test
    void 관리자는_가상_예보를_일괄_취소하고_시청_관계자는_403이다() throws Exception {
        simulate(Role.ADMIN, 80, "12.5", "2032-06-05T15:00:00")
                .andExpect(jsonPath("$.data.applied").value("CONFIRMED"));

        cancelSimulated(Role.CITY_OFFICIAL).andExpect(status().isForbidden());
        cancelSimulated(Role.USER).andExpect(status().isForbidden());
        cancelSimulated(Role.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cancelledForecastCount").value(1))
                .andExpect(jsonPath("$.data.cancelledRequestCount").value(0));
        cancelSimulated(Role.ADMIN)
                .andExpect(jsonPath("$.data.cancelledForecastCount").value(0));
        // 취소 뒤 같은 날짜로 바로 다시 만들 수 있다
        simulate(Role.ADMIN, 80, "12.5", "2032-06-05T15:00:00")
                .andExpect(jsonPath("$.data.applied").value("CONFIRMED"));
    }

    private ResultActions cancelSimulated(Role role) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/forecasts/simulated/cancel")
                .header("Authorization",
                        "Bearer " + jwtTokenProvider.createAccessToken(1L, role)));
    }

    private ResultActions simulate(Role role, int pop, String mm, String rainStartAt)
            throws Exception {
        String token = jwtTokenProvider.createAccessToken(1L, role);
        return mockMvc.perform(post("/api/v1/admin/forecasts/simulate")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pop\":" + pop + ",\"mm\":" + mm + ",\"rainStartAt\":\""
                        + rainStartAt + "\"}"));
    }
}
