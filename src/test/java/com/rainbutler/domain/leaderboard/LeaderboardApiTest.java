package com.rainbutler.domain.leaderboard;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rainbutler.domain.request.RequestFixtures;
import com.rainbutler.domain.user.entity.Role;
import com.rainbutler.global.security.JwtTokenProvider;
import com.rainbutler.support.IntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 리더보드 주간 · 누적 집계와 내 순위 API를 검증합니다.
 */
@AutoConfigureMockMvc
class LeaderboardApiTest extends IntegrationTestSupport {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    /** 2026-11-04 (수) 12:00 · 이번 주 월요일은 11-02 */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 4, 12, 0);
    private static final LocalDateTime LAST_WEEK =
            LocalDateTime.of(2026, 11, 1, 23, 0);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Clock clock;

    private RequestFixtures fixtures;

    @BeforeEach
    void setUp() {
        given(clock.getZone()).willReturn(SEOUL);
        given(clock.instant()).willReturn(NOW.atZone(SEOUL).toInstant());
        jdbcTemplate.execute("TRUNCATE TABLE point_history, users RESTART IDENTITY CASCADE");
        fixtures = new RequestFixtures(jdbcTemplate);
    }

    @Test
    void 주간_순위는_이번_주_점수만_합산하고_동점은_같은_순위다() throws Exception {
        Long a = fixtures.user("에이");
        Long b = fixtures.user("비");
        Long c = fixtures.user("씨");
        Long d = fixtures.user("디");
        Long admin = fixtures.user("관리자");
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", admin);

        point(a, 10, "EARN", NOW.minusDays(2));
        point(b, 10, "EARN", NOW.minusDays(1));
        point(c, 5, "EARN", NOW.minusHours(3));
        point(c, 100, "EARN", LAST_WEEK);
        point(d, 2, "EARN", NOW.minusHours(2));
        // 포인트 이력이 없는 0점 사용자도 순위에 포함된다
        fixtures.user("이");
        point(d, 3, "REVOKE", NOW.minusHours(2));
        point(admin, 50, "EARN", NOW.minusHours(1));

        list(token(a), "type", "WEEKLY")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("WEEKLY"))
                .andExpect(jsonPath("$.data.periodStart").value("2026-11-02T00:00:00"))
                .andExpect(jsonPath("$.data.totalParticipants").value(4))
                .andExpect(jsonPath("$.data.rankings[*].nickname")
                        .value(contains("에이", "비", "씨", "이")))
                .andExpect(jsonPath("$.data.rankings[*].rank").value(contains(1, 1, 3, 4)))
                .andExpect(jsonPath("$.data.rankings[*].score").value(contains(10, 10, 5, 0)))
                .andExpect(jsonPath("$.data.rankings[0].isMe").value(true))
                .andExpect(jsonPath("$.data.rankings[1].isMe").value(false))
                .andExpect(jsonPath("$.data.me.rank").value(1))
                .andExpect(jsonPath("$.data.me.score").value(10));
    }

    @Test
    void 상위_limit_밖이어도_me에_내_순위가_내려간다() throws Exception {
        point(fixtures.user("에이"), 20, "EARN", NOW);
        point(fixtures.user("비"), 15, "EARN", NOW);
        Long c = fixtures.user("씨");
        point(c, 5, "EARN", NOW);

        list(token(c), "type", "WEEKLY", "limit", "2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rankings[*].nickname").value(contains("에이", "비")))
                .andExpect(jsonPath("$.data.totalParticipants").value(3))
                .andExpect(jsonPath("$.data.me.rank").value(3))
                .andExpect(jsonPath("$.data.me.score").value(5))
                .andExpect(jsonPath("$.data.me.isMe").value(true));
    }

    @Test
    void 누적_순위는_지난_주_점수도_합산하고_periodStart가_null이다() throws Exception {
        Long a = fixtures.user("에이");
        Long b = fixtures.user("비");
        Long c = fixtures.user("씨");
        Long d = fixtures.user("디");
        Long admin = fixtures.user("관리자");
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", admin);

        point(a, 10, "EARN", NOW.minusDays(2));
        point(b, 10, "EARN", NOW.minusDays(1));
        point(c, 5, "EARN", NOW.minusHours(3));
        point(c, 100, "EARN", LAST_WEEK);
        point(d, 2, "EARN", NOW);
        point(d, 3, "REVOKE", NOW);
        point(admin, 50, "EARN", NOW);

        list(token(c), "type", "TOTAL")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("TOTAL"))
                .andExpect(jsonPath("$.data.periodStart").value(nullValue()))
                .andExpect(jsonPath("$.data.totalParticipants").value(3))
                .andExpect(jsonPath("$.data.rankings[*].nickname")
                        .value(contains("씨", "에이", "비")))
                .andExpect(jsonPath("$.data.rankings[*].score").value(contains(105, 10, 10)))
                .andExpect(jsonPath("$.data.rankings[*].rank").value(contains(1, 2, 2)))
                .andExpect(jsonPath("$.data.me.rank").value(1))
                .andExpect(jsonPath("$.data.me.score").value(105));
    }

    @Test
    void 포인트_이력이_없어도_0점으로_순위에_나온다() throws Exception {
        Long me = fixtures.user("나");

        list(token(me), "type", "WEEKLY")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rankings[0].nickname").value("나"))
                .andExpect(jsonPath("$.data.rankings[0].rank").value(1))
                .andExpect(jsonPath("$.data.rankings[0].score").value(0))
                .andExpect(jsonPath("$.data.totalParticipants").value(1))
                .andExpect(jsonPath("$.data.me.rank").value(1))
                .andExpect(jsonPath("$.data.me.score").value(0))
                .andExpect(jsonPath("$.data.me.nickname").value("나"));
    }

    @Test
    void 내_순위만_조회한다() throws Exception {
        Long a = fixtures.user("에이");
        point(a, 10, "EARN", NOW);
        point(fixtures.user("비"), 20, "EARN", NOW);

        mockMvc.perform(get("/api/v1/leaderboard/me")
                        .param("type", "WEEKLY")
                        .header("Authorization", "Bearer " + token(a)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("WEEKLY"))
                .andExpect(jsonPath("$.data.periodStart").value("2026-11-02T00:00:00"))
                .andExpect(jsonPath("$.data.totalParticipants").value(2))
                .andExpect(jsonPath("$.data.rank").value(2))
                .andExpect(jsonPath("$.data.score").value(10));
    }

    @Test
    void type은_대소문자_구분없이_받고_ZONE은_400이다() throws Exception {
        Long me = fixtures.user("나");
        point(me, 10, "EARN", NOW);

        list(token(me), "type", "weekly")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("WEEKLY"));

        list(token(me), "type", "ZONE")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        list(token(me), "limit", "0")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    private ResultActions list(String token, String... params) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/leaderboard")
                .header("Authorization", "Bearer " + token);
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request);
    }

    private String token(Long userId) {
        return jwtTokenProvider.createAccessToken(userId, Role.USER);
    }

    private void point(Long userId, int amount, String type, LocalDateTime createdAt) {
        jdbcTemplate.update("INSERT INTO point_history (user_id, source_type, source_id, "
                        + "amount, type, description, created_at, updated_at) "
                        + "VALUES (?, 'INSPECTION', 1, ?, ?, '테스트', ?, ?)",
                userId, amount, type, Timestamp.valueOf(createdAt), Timestamp.valueOf(createdAt));
    }
}
