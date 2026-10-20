package com.rainbutler.domain.user;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.global.security.JwtTokenProvider;
import com.rainbutler.support.IntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 내 정보의 참여 점수 · 입양 수 · 제재 · 쉬기 상태를 검증합니다. (시각은 Clock으로 고정)
 */
@AutoConfigureMockMvc
class MeStatsApiTest extends IntegrationTestSupport {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 12, 0);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Clock clock;

    private User user;
    private String token;

    @BeforeEach
    void setUp() {
        given(clock.getZone()).willReturn(SEOUL);
        given(clock.instant()).willReturn(NOW.atZone(SEOUL).toInstant());
        user = userRepository.save(User.local(
                UUID.randomUUID() + "@test.com", "{noop}x", "통계집사"));
        token = jwtTokenProvider.createAccessToken(user.getId(), user.getRole());
    }

    @Test
    void 아무_활동이_없으면_0과_빈_상태이고_기존_필드는_그대로다() throws Exception {
        me().andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("통계집사"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.point").value(0))
                .andExpect(jsonPath("$.data.onboardingCompleted").value(false))
                .andExpect(jsonPath("$.data.participationScore").value(0))
                .andExpect(jsonPath("$.data.adoptionCount").value(0))
                .andExpect(jsonPath("$.data.sanction.active").value(false))
                .andExpect(jsonPath("$.data.sanction.until").doesNotExist())
                .andExpect(jsonPath("$.data.sanction.permanent").value(false))
                .andExpect(jsonPath("$.data.pause.activeUntil").doesNotExist())
                .andExpect(jsonPath("$.data.pause.nextAvailableAt").doesNotExist());
    }

    @Test
    void 참여_점수는_적립에서_회수를_뺀_값이다() throws Exception {
        point(30, "EARN");
        point(5, "EARN");
        point(5, "REVOKE");
        // 회수를 음수로 저장한 경우도 같은 결과
        point(-10, "REVOKE");

        me().andExpect(jsonPath("$.data.participationScore").value(20));
    }

    @Test
    void 입양_수는_입양_중인_것만_센다() throws Exception {
        adoption("ACTIVE");
        adoption("ACTIVE");
        adoption("RELEASED");

        me().andExpect(jsonPath("$.data.adoptionCount").value(2));
    }

    @Test
    void 기간_제재_중이면_종료_시각을_준다() throws Exception {
        sanction(NOW.minusDays(1), NOW.plusDays(6), false);

        me().andExpect(jsonPath("$.data.sanction.active").value(true))
                .andExpect(jsonPath("$.data.sanction.until").value("2026-10-26T12:00:00"))
                .andExpect(jsonPath("$.data.sanction.permanent").value(false));
    }

    @Test
    void 영구_제재면_종료_시각_없이_permanent다() throws Exception {
        sanction(NOW.minusDays(1), null, true);

        me().andExpect(jsonPath("$.data.sanction.active").value(true))
                .andExpect(jsonPath("$.data.sanction.until").doesNotExist())
                .andExpect(jsonPath("$.data.sanction.permanent").value(true));
    }

    @Test
    void 끝난_제재는_제재_중이_아니다() throws Exception {
        sanction(NOW.minusDays(10), NOW.minusDays(3), false);

        me().andExpect(jsonPath("$.data.sanction.active").value(false));
    }

    @Test
    void 쉬는_중이면_종료_시각과_다음_신청_가능_시각을_준다() throws Exception {
        pause(NOW.plusDays(10), NOW.minusDays(4));

        me().andExpect(jsonPath("$.data.pause.activeUntil").value("2026-10-30T12:00:00"))
                .andExpect(jsonPath("$.data.pause.nextAvailableAt").value("2027-01-14T12:00:00"));
    }

    @Test
    void 쉬기가_끝나고_쿨다운_중이면_다음_신청_가능_시각만_준다() throws Exception {
        pause(NOW.minusDays(6), NOW.minusDays(20));

        me().andExpect(jsonPath("$.data.pause.activeUntil").doesNotExist())
                .andExpect(jsonPath("$.data.pause.nextAvailableAt").value("2026-12-29T12:00:00"));
    }

    private ResultActions me() throws Exception {
        return mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token));
    }

    private void point(int amount, String type) {
        jdbcTemplate.update("INSERT INTO point_history (user_id, source_type, source_id, amount, "
                + "type, created_at, updated_at) VALUES (?, 'CLEANING', 1, ?, ?, now(), now())",
                user.getId(), amount, type);
    }

    private void adoption(String status) {
        Long drain = jdbcTemplate.queryForObject("INSERT INTO drain (registered_by, latitude, "
                + "longitude, photo_url, dirt_level, adoption_status, status, is_risk, "
                + "created_at, updated_at) VALUES (?, 37.5, 127.0, 'http://x', 1, 'ADOPTED', "
                + "'ACTIVE', false, now(), now()) RETURNING id", Long.class, user.getId());
        Long cleaning = jdbcTemplate.queryForObject("INSERT INTO cleaning (user_id, drain_id, "
                + "type, before_photo_url, status, review_status, latitude, longitude, "
                + "location_mismatch, started_at, created_at, updated_at) VALUES (?, ?, "
                + "'ADOPTION', 'http://x', 'COMPLETED', 'NOT_REQUIRED', 37.5, 127.0, false, "
                + "now(), now(), now()) RETURNING id", Long.class, user.getId(), drain);
        jdbcTemplate.update("INSERT INTO adoption (user_id, drain_id, cleaning_id, nickname, "
                + "status, level, score, ignore_count, adopted_at, created_at, updated_at) "
                + "VALUES (?, ?, ?, '또랑이', ?, 1, 0, 0, now(), now(), now())",
                user.getId(), drain, cleaning, status);
    }

    private void sanction(LocalDateTime startsAt, LocalDateTime until, boolean permanent) {
        jdbcTemplate.update("INSERT INTO sanction (user_id, reason, starts_at, until, permanent, "
                + "created_at, updated_at) VALUES (?, '허위 제보', ?, ?, ?, now(), now())",
                user.getId(), Timestamp.valueOf(startsAt),
                until == null ? null : Timestamp.valueOf(until), permanent);
    }

    private void pause(LocalDateTime pauseUntil, LocalDateTime requestedAt) {
        jdbcTemplate.update("UPDATE users SET pause_until = ?, last_pause_requested_at = ? "
                + "WHERE id = ?", Timestamp.valueOf(pauseUntil), Timestamp.valueOf(requestedAt),
                user.getId());
    }
}
