package com.rainbutler.domain.request;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rainbutler.domain.user.entity.Role;
import com.rainbutler.global.security.JwtTokenProvider;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 내 비 예보 요청 목록의 역할별 포함 · 제외, 행동, 정렬, 위치 선택을 검증합니다.
 */
@AutoConfigureMockMvc
class MyRequestListApiTest extends RequestIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2027, 11, 3, 10, 0);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private Long me;
    private Long forecast;
    private Long ownSent;
    private Long claimed;
    private Long nearMyDrain;
    private Long farOpen;
    private Long ownCompleted;

    @BeforeEach
    void setUp() {
        now(NOW);
        LocalDate today = NOW.toLocalDate();
        forecast = fixtures.forecast(today, today.atTime(18, 0), false);
        me = fixtures.user("나");
        Long other = fixtures.user("남");

        // OWNER: 응답 전 (응답 마감 11:00)
        Long myDrain = fixtures.drain(RequestFixtures.BASE_LAT, RequestFixtures.BASE_LNG, false);
        ownSent = fixtures.request(forecast, myDrain, fixtures.adoption(me, myDrain, "또랑이"),
                "SENT", NOW.plusHours(1), NOW.withHour(17));
        // CLAIMANT: 내가 맡은 요청 (완료 마감 15:00)
        Long otherDrain = fixtures.drain(RequestFixtures.north(5000), RequestFixtures.BASE_LNG,
                false);
        claimed = fixtures.request(forecast, otherDrain,
                fixtures.adoption(other, otherDrain, "남의몽"), "OPEN", NOW, NOW.withHour(15));
        fixtures.claim(claimed, me);
        // NEARBY: 내 또랑이에서 250m (완료 마감 16:00, 미입양 위험)
        nearMyDrain = fixtures.request(forecast,
                fixtures.drain(RequestFixtures.north(250), RequestFixtures.BASE_LNG, true), null,
                "OPEN", NOW, NOW.withHour(16));
        // 멀리 있는 공개 요청: 위치를 보내야만 보인다
        farOpen = fixtures.request(forecast,
                fixtures.drain(RequestFixtures.north(3000), RequestFixtures.BASE_LNG, true), null,
                "OPEN", NOW, NOW.withHour(16));
        // 끝난 내 요청 (완료 1시간 전) → 맨 뒤
        Long doneDrain = fixtures.drain(RequestFixtures.north(20), RequestFixtures.BASE_LNG, false);
        ownCompleted = fixtures.request(forecast, doneDrain,
                fixtures.adoption(me, doneDrain, "완료몽"), "COMPLETED", NOW, NOW.withHour(17));
        jdbcTemplate.update("UPDATE inspection_request SET completed_at = ? WHERE id = ?",
                Timestamp.valueOf(NOW.minusHours(1)), ownCompleted);
        // 제외: 취소된 내 요청, 이틀 전에 놓친 내 요청
        Long cancelledDrain = fixtures.drain(RequestFixtures.north(30), RequestFixtures.BASE_LNG,
                false);
        fixtures.request(forecast, cancelledDrain, fixtures.adoption(me, cancelledDrain, "취소몽"),
                "CANCELLED", NOW, NOW.withHour(17));
        Long missedDrain = fixtures.drain(RequestFixtures.north(40), RequestFixtures.BASE_LNG,
                false);
        Long missed = fixtures.request(forecast, missedDrain,
                fixtures.adoption(me, missedDrain, "놓친몽"), "MISSED", NOW, NOW.withHour(17));
        jdbcTemplate.update("UPDATE inspection_request SET missed_at = ? WHERE id = ?",
                Timestamp.valueOf(NOW.minusDays(2)), missed);
    }

    @Test
    void 위치_없이_부르면_내_요청_맡은_요청_내_또랑이_근처_요청을_마감순으로_준다() throws Exception {
        list(null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(4)))
                .andExpect(jsonPath("$.data[*].requestId")
                        .value(contains(ownSent.intValue(), claimed.intValue(),
                                nearMyDrain.intValue(), ownCompleted.intValue())))
                .andExpect(jsonPath("$.data[0].role").value("OWNER"))
                .andExpect(jsonPath("$.data[0].drain.name").value("또랑이"))
                .andExpect(jsonPath("$.data[0].availableActions",
                        contains("REPLY", "INSPECT")))
                .andExpect(jsonPath("$.data[0].remainingMinutes").value(60))
                .andExpect(jsonPath("$.data[0].distanceM").value(nullValue()))
                .andExpect(jsonPath("$.data[0].rainStartAt").value("2027-11-03T18:00:00"))
                .andExpect(jsonPath("$.data[1].role").value("CLAIMANT"))
                .andExpect(jsonPath("$.data[1].availableActions", contains("INSPECT")))
                .andExpect(jsonPath("$.data[1].remainingMinutes").value(300))
                .andExpect(jsonPath("$.data[2].role").value("NEARBY"))
                .andExpect(jsonPath("$.data[2].drain.name").value(nullValue()))
                .andExpect(jsonPath("$.data[2].availableActions", contains("CLAIM")))
                .andExpect(jsonPath("$.data[3].state").value("COMPLETED"))
                .andExpect(jsonPath("$.data[3].availableActions", empty()))
                .andExpect(jsonPath("$.data[3].remainingMinutes").value(nullValue()));
    }

    @Test
    void 위치를_보내면_그_근처_공개_요청과_거리도_준다() throws Exception {
        list(RequestFixtures.north(3100), RequestFixtures.BASE_LNG)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(5)))
                .andExpect(jsonPath("$.data[?(@.requestId == " + farOpen + ")].role")
                        .value(contains("NEARBY")))
                .andExpect(jsonPath("$.data[?(@.requestId == " + farOpen + ")].distanceM")
                        .value(contains(100)));
    }

    @Test
    void 응답_마감이_지난_SENT는_점검만_할_수_있다() throws Exception {
        now(NOW.plusHours(2));

        list(null, null)
                .andExpect(jsonPath("$.data[?(@.requestId == " + ownSent + ")].availableActions")
                        .value(contains(contains("INSPECT"))));
    }

    @Test
    void 위도_경도_중_하나만_보내면_400이다() throws Exception {
        mockMvc.perform(get("/api/v1/requests/me").param("latitude", "37.5")
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    private ResultActions list(Double latitude, Double longitude) throws Exception {
        var request = get("/api/v1/requests/me").header("Authorization", "Bearer " + token());
        if (latitude != null) {
            request.param("latitude", String.valueOf(latitude))
                    .param("longitude", String.valueOf(longitude));
        }
        return mockMvc.perform(request);
    }

    private String token() {
        return jwtTokenProvider.createAccessToken(me, Role.USER);
    }
}
