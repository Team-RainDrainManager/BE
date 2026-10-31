package com.rainbutler.domain.drain;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.global.security.JwtTokenProvider;
import com.rainbutler.support.IntegrationTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 빗물받이 성장 조회의 로그인 · 입양 귀속 · 레벨 계산을 검증합니다.
 */
@AutoConfigureMockMvc
class DrainGrowthApiTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long userId;
    private String token;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.local(
                UUID.randomUUID() + "@test.com", "{noop}x", "성장집사"));
        userId = user.getId();
        token = jwtTokenProvider.createAccessToken(userId, user.getRole());
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        Long drainId = drain();

        mockMvc.perform(get("/api/v1/drains/{drainId}/growth", drainId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void ACTIVE_입양의_레벨과_점수로_성장을_준다() throws Exception {
        Long drainId = drain();
        adoption(drainId, 2, 100);

        growth(drainId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.level").value(2))
                .andExpect(jsonPath("$.data.score").value(100))
                .andExpect(jsonPath("$.data.nextLevelScore").value(150))
                .andExpect(jsonPath("$.data.progressPercent").value(50));
    }

    @Test
    void 만렙이면_진행률은_100이고_다음_레벨_점수는_현재_점수다() throws Exception {
        Long drainId = drain();
        adoption(drainId, 10, 4000);

        growth(drainId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.level").value(10))
                .andExpect(jsonPath("$.data.nextLevelScore").value(4000))
                .andExpect(jsonPath("$.data.progressPercent").value(100));
    }

    @Test
    void ACTIVE_입양이_없으면_ADOPTION_NOT_FOUND() throws Exception {
        Long drainId = drain();

        growth(drainId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ADOPTION_NOT_FOUND"));
    }

    @Test
    void 없는_빗물받이면_DRAIN_NOT_FOUND() throws Exception {
        growth(999_999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("DRAIN_NOT_FOUND"));
    }

    private ResultActions growth(Long drainId) throws Exception {
        return mockMvc.perform(get("/api/v1/drains/{drainId}/growth", drainId)
                .header("Authorization", "Bearer " + token));
    }

    private Long drain() {
        return jdbcTemplate.queryForObject("INSERT INTO drain (registered_by, latitude, "
                + "longitude, photo_url, dirt_level, adoption_status, status, is_risk, "
                + "created_at, updated_at) VALUES (?, 37.5, 127.0, 'http://x', 1, 'ADOPTED', "
                + "'ACTIVE', false, now(), now()) RETURNING id", Long.class, userId);
    }

    private Long adoption(Long drainId, int level, int score) {
        Long cleaningId = jdbcTemplate.queryForObject("INSERT INTO cleaning (user_id, drain_id, "
                + "type, before_photo_url, status, review_status, latitude, longitude, "
                + "location_mismatch, started_at, created_at, updated_at) VALUES (?, ?, "
                + "'ADOPTION', 'http://x', 'COMPLETED', 'NOT_REQUIRED', 37.5, 127.0, false, "
                + "now(), now(), now()) RETURNING id", Long.class, userId, drainId);
        return jdbcTemplate.queryForObject("INSERT INTO adoption (user_id, drain_id, "
                + "cleaning_id, nickname, status, level, score, ignore_count, adopted_at, "
                + "created_at, updated_at) VALUES (?, ?, ?, '또랑이', 'ACTIVE', ?, ?, 0, now(), "
                + "now(), now()) RETURNING id", Long.class, userId, drainId, cleaningId, level,
                score);
    }
}
