package com.rainbutler.domain.request;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 지도 빗물받이의 이번 비 대신 점검 상태(rainRequest)와 rainStatus 필터를 검증합니다.
 */
@AutoConfigureMockMvc
class DrainRainStatusApiTest extends RequestIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2028, 7, 1, 10, 0);
    private static final LocalDate TOMORROW = NOW.toLocalDate().plusDays(1);

    @Autowired
    private MockMvc mockMvc;

    private Long open;
    private Long sent;
    private Long done;
    private Long expired;
    private Long none;
    private Long openRequest;

    @BeforeEach
    void setUp() {
        now(NOW);
        open = drain(0);
        sent = drain(10);
        done = drain(20);
        expired = drain(30);
        none = drain(40);
    }

    @Test
    void 이번_비_요청_상태를_지도_상태로_붙인다() throws Exception {
        givenForecast();

        map()
                .andExpect(status().isOk())
                .andExpect(rain(open, "status").value(contains("NEEDS_HELP")))
                .andExpect(rain(open, "requestId").value(contains(openRequest.intValue())))
                .andExpect(rain(sent, "status").value(contains("IN_PROGRESS")))
                .andExpect(rain(done, "status").value(contains("DONE")))
                .andExpect(jsonPath("$.data[?(@.id == " + expired + ")].rainRequest")
                        .value(contains(nullValue())))
                .andExpect(jsonPath("$.data[?(@.id == " + none + ")].rainRequest")
                        .value(contains(nullValue())));
    }

    @Test
    void rainStatus로_거른다() throws Exception {
        givenForecast();

        map("rainStatus", "NEEDS_HELP")
                .andExpect(jsonPath("$.data[*].id").value(contains(open.intValue())));
        map("rainStatus", "in_progress")
                .andExpect(jsonPath("$.data[*].id").value(contains(sent.intValue())));
        map("rainStatus", "DONE")
                .andExpect(jsonPath("$.data[*].id").value(contains(done.intValue())));
        mockMvc.perform(get("/api/v1/drain/list").param("rainStatus", "NEEDS_HELP")
                        .param("size", "100"))
                .andExpect(jsonPath("$.data.content[*].id").value(contains(open.intValue())));
        map("rainStatus", "WHATEVER").andExpect(status().isBadRequest());
    }

    @Test
    void 이번_비_예보가_없으면_모두_null이고_필터_결과는_없다() throws Exception {
        map()
                .andExpect(jsonPath("$.data[*].id").value(containsInAnyOrder(open.intValue(),
                        sent.intValue(), done.intValue(), expired.intValue(), none.intValue())))
                .andExpect(jsonPath("$.data[*].rainRequest").value(contains(nullValue(),
                        nullValue(), nullValue(), nullValue(), nullValue())));
        map("rainStatus", "NEEDS_HELP").andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void 상세에도_이번_비_요청_상태를_준다() throws Exception {
        givenForecast();

        mockMvc.perform(get("/api/v1/drain/" + open))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rainRequest.requestId").value(openRequest))
                .andExpect(jsonPath("$.data.rainRequest.status").value("NEEDS_HELP"));
        mockMvc.perform(get("/api/v1/drain/" + none))
                .andExpect(jsonPath("$.data.rainRequest").value(nullValue()));
    }

    private void givenForecast() {
        Long forecast = fixtures.forecast(TOMORROW, TOMORROW.atTime(15, 0), false);
        LocalDateTime deadline = NOW.withHour(20);
        openRequest = fixtures.request(forecast, open, null, "OPEN", NOW.withHour(12), deadline);
        fixtures.request(forecast, sent, null, "SENT", NOW.withHour(12), deadline);
        fixtures.request(forecast, done, null, "COMPLETED", NOW.withHour(12), deadline);
        fixtures.request(forecast, expired, null, "EXPIRED", NOW.withHour(12), deadline);
    }

    private Long drain(double metersNorth) {
        return fixtures.drain(RequestFixtures.north(metersNorth), RequestFixtures.BASE_LNG, false);
    }

    private org.springframework.test.web.servlet.result.JsonPathResultMatchers rain(
            Long drainId, String field) {
        return jsonPath("$.data[?(@.id == " + drainId + ")].rainRequest." + field);
    }

    private ResultActions map(String... params) throws Exception {
        var request = get("/api/v1/drain")
                .param("minLatitude", String.valueOf(RequestFixtures.BASE_LAT - 0.001))
                .param("maxLatitude", String.valueOf(RequestFixtures.BASE_LAT + 0.001))
                .param("minLongitude", String.valueOf(RequestFixtures.BASE_LNG - 0.001))
                .param("maxLongitude", String.valueOf(RequestFixtures.BASE_LNG + 0.001));
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request);
    }
}
