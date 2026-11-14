package com.rainbutler.domain.adoption;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 입양 빗물받이 별명 중복 확인의 인증 · 중복 판정 · 검증을 확인합니다.
 */
@AutoConfigureMockMvc
class AdoptionNicknameCheckApiTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private Long userId;
    private String token;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.local(
                UUID.randomUUID() + "@test.com", "{noop}x", "입양집사"));
        userId = user.getId();
        token = jwtTokenProvider.createAccessToken(userId, user.getRole());
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        mockMvc.perform(get("/api/v1/adoptions/nickname/check").param("nickname", "또랑이"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void 사용할_수_있는_별명이면_available_true() throws Exception {
        check("또랑이", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nickname").value("또랑이"))
                .andExpect(jsonPath("$.data.available").value(true));
    }

    @Test
    void 앞뒤_공백을_지우고_확인한다() throws Exception {
        User other = userRepository.save(User.local(
                UUID.randomUUID() + "@test.com", "{noop}x", "다른집사"));
        Long drainId = drain(other.getId(), "ADOPTED");
        adoption(other.getId(), drainId, cleaning(other.getId(), drainId), "ACTIVE", "또랑이");

        check("  또랑이  ", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("또랑이"))
                .andExpect(jsonPath("$.data.available").value(false));
    }

    @Test
    void 다른_사용자의_입양이_쓰는_별명이어도_중복이다() throws Exception {
        User other = userRepository.save(User.local(
                UUID.randomUUID() + "@test.com", "{noop}x", "다른집사"));
        Long drainId = drain(other.getId(), "ADOPTED");
        adoption(other.getId(), drainId, cleaning(other.getId(), drainId), "ACTIVE", "또랑이");

        check("또랑이", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false));
    }

    @Test
    void 대소문자가_달라도_같은_별명으로_본다() throws Exception {
        User other = userRepository.save(User.local(
                UUID.randomUUID() + "@test.com", "{noop}x", "다른집사"));
        Long drainId = drain(other.getId(), "ADOPTED");
        adoption(other.getId(), drainId, cleaning(other.getId(), drainId), "ACTIVE", "Toto");

        check("toto", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false));
    }

    @Test
    void 해제된_입양의_별명은_중복으로_보지_않는다() throws Exception {
        User other = userRepository.save(User.local(
                UUID.randomUUID() + "@test.com", "{noop}x", "다른집사"));
        Long drainId = drain(other.getId(), "AVAILABLE");
        adoption(other.getId(), drainId, cleaning(other.getId(), drainId), "RELEASED", "또랑이");

        check("또랑이", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true));
    }

    @Test
    void 별명을_바꿀_때_자기_입양은_중복에서_뺀다() throws Exception {
        Long drainId = drain(userId, "ADOPTED");
        Long adoptionId = adoption(
                userId, drainId, cleaning(userId, drainId), "ACTIVE", "또랑이");

        check("또랑이", adoptionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true));
    }

    @Test
    void 빈_별명이면_400() throws Exception {
        check(" ", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("nickname"));
    }

    @Test
    void 별명이_20자를_넘으면_400() throws Exception {
        check("가".repeat(21), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("nickname"));
    }

    private ResultActions check(String nickname, Long excludeAdoptionId) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/adoptions/nickname/check")
                .header("Authorization", "Bearer " + token)
                .param("nickname", nickname);
        if (excludeAdoptionId != null) {
            request = request.param("excludeAdoptionId", String.valueOf(excludeAdoptionId));
        }
        return mockMvc.perform(request);
    }

    private Long drain(Long registeredBy, String adoptionStatus) {
        return jdbcTemplate.queryForObject("INSERT INTO drain (registered_by, latitude, "
                + "longitude, photo_url, dirt_level, adoption_status, status, is_risk, "
                + "created_at, updated_at) VALUES (?, 37.5, 127.0, 'http://x', 1, ?, 'ACTIVE', "
                + "false, now(), now()) RETURNING id", Long.class, registeredBy, adoptionStatus);
    }

    private Long cleaning(Long ownerId, Long drainId) {
        return jdbcTemplate.queryForObject("INSERT INTO cleaning (user_id, drain_id, "
                + "type, before_photo_url, status, review_status, latitude, longitude, "
                + "location_mismatch, started_at, created_at, updated_at) VALUES (?, ?, "
                + "'ADOPTION', 'http://x', 'COMPLETED', 'NOT_REQUIRED', 37.5, 127.0, false, "
                + "now(), now(), now()) RETURNING id", Long.class, ownerId, drainId);
    }

    private Long adoption(
            Long ownerId, Long drainId, Long cleaningId, String status, String nickname) {
        return jdbcTemplate.queryForObject("INSERT INTO adoption (user_id, drain_id, "
                + "cleaning_id, nickname, status, level, score, ignore_count, adopted_at, "
                + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, 1, 0, 0, now(), "
                + "now(), now()) RETURNING id", Long.class,
                ownerId, drainId, cleaningId, nickname, status);
    }
}
