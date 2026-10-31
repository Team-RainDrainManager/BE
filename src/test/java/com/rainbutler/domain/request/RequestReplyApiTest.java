package com.rainbutler.domain.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rainbutler.domain.user.entity.Role;
import com.rainbutler.global.security.JwtTokenProvider;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 비 예보 요청 가능 · 불가 응답 API를 검증합니다.
 */
@AutoConfigureMockMvc
class RequestReplyApiTest extends RequestIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2027, 7, 1, 9, 0);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private Long owner;
    private Long adoption;
    private Long request;

    @BeforeEach
    void setUp() {
        now(NOW);
        owner = fixtures.user("집사");
        Long drain = fixtures.drain(RequestFixtures.BASE_LAT, RequestFixtures.BASE_LNG, false);
        adoption = fixtures.adoption(owner, drain, "또랑이");
        jdbcTemplate.update("UPDATE adoption SET ignore_count = 2 WHERE id = ?", adoption);
        LocalDate tomorrow = NOW.toLocalDate().plusDays(1);
        Long forecast = fixtures.forecast(tomorrow, tomorrow.atTime(15, 0), false);
        request = fixtures.request(forecast, drain, adoption, "SENT", NOW.withHour(12),
                NOW.withHour(20));
    }

    @Test
    void 가능으로_응답하면_ACCEPTED이고_연속_무응답이_초기화된다() throws Exception {
        reply(owner, request, "ACCEPT")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestId").value(request))
                .andExpect(jsonPath("$.data.state").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.repliedAt").value("2027-07-01T09:00:00"));

        assertThat(ignoreCount()).isZero();
    }

    @Test
    void 불가로_응답하면_대신_점검으로_공개되고_근처_입양자에게_알린다() throws Exception {
        Long helper = fixtures.user("이웃");
        fixtures.adoption(helper,
                fixtures.drain(RequestFixtures.north(100), RequestFixtures.BASE_LNG, false), "이웃몽");

        reply(owner, request, "DECLINE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("OPEN"));

        assertThat(jdbcTemplate.queryForObject("SELECT reply FROM inspection_request "
                + "WHERE id = ?", String.class, request)).isEqualTo("DECLINE");
        assertThat(ignoreCount()).isZero();
        assertThat(fixtures.notificationCount(helper, "SUBSTITUTE_OPEN")).isEqualTo(1);
    }

    @Test
    void 요청의_입양자가_아니면_403이다() throws Exception {
        reply(fixtures.user("남"), request, "ACCEPT")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void 온보딩을_마치지_않았으면_403이다() throws Exception {
        jdbcTemplate.update("UPDATE users SET onboarding_completed_at = null WHERE id = ?", owner);

        reply(owner, request, "ACCEPT")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ONBOARDING_REQUIRED"));
    }

    @Test
    void 응답_마감이_지났거나_이미_응답했으면_409다() throws Exception {
        now(NOW.withHour(12));
        reply(owner, request, "ACCEPT")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REQUEST_REPLY_NOT_ALLOWED"));

        now(NOW);
        reply(owner, request, "ACCEPT").andExpect(status().isOk());
        reply(owner, request, "DECLINE")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REQUEST_REPLY_NOT_ALLOWED"));
    }

    @Test
    void 없는_요청은_404이고_응답값이_없으면_400이다() throws Exception {
        reply(owner, 999_999L, "ACCEPT")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("REQUEST_NOT_FOUND"));
        mockMvc.perform(post("/api/v1/requests/" + request + "/reply")
                        .header("Authorization", "Bearer " + token(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    private ResultActions reply(Long userId, Long requestId, String reply) throws Exception {
        return mockMvc.perform(post("/api/v1/requests/" + requestId + "/reply")
                .header("Authorization", "Bearer " + token(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reply\":\"" + reply + "\"}"));
    }

    private String token(Long userId) {
        return jwtTokenProvider.createAccessToken(userId, Role.USER);
    }

    private int ignoreCount() {
        return jdbcTemplate.queryForObject(
                "SELECT ignore_count FROM adoption WHERE id = ?", Integer.class, adoption);
    }
}
