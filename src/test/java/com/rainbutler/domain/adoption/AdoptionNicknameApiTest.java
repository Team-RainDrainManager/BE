package com.rainbutler.domain.adoption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.global.security.JwtTokenProvider;
import com.rainbutler.support.IntegrationTestSupport;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 입양 빗물받이 별명 변경의 권한 · 상태 · 검증을 확인합니다.
 */
@AutoConfigureMockMvc
class AdoptionNicknameApiTest extends IntegrationTestSupport {

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
        Long drainId = drain(userId, "ADOPTED");
        Long adoptionId = adoption(userId, drainId, cleaning(userId, drainId), "ACTIVE");

        mockMvc.perform(patch("/api/v1/adoptions/{adoptionId}/nickname", adoptionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("새또랑이")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void 본인_입양의_별명을_바꾼다() throws Exception {
        Long drainId = drain(userId, "ADOPTED");
        Long adoptionId = adoption(userId, drainId, cleaning(userId, drainId), "ACTIVE");

        update(adoptionId, "새또랑이")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.adoptionId").value(adoptionId))
                .andExpect(jsonPath("$.data.nickname").value("새또랑이"))
                .andExpect(jsonPath("$.data.updatedAt").exists());

        Map<String, Object> adoption = jdbcTemplate.queryForMap(
                "SELECT nickname, updated_at FROM adoption WHERE id = ?", adoptionId);
        assertThat(adoption.get("nickname")).isEqualTo("새또랑이");
        assertThat(adoption.get("updated_at")).isNotNull();
    }

    @Test
    void 다른_사람의_입양_별명은_바꿀_수_없다() throws Exception {
        User other = userRepository.save(User.local(
                UUID.randomUUID() + "@test.com", "{noop}x", "다른집사"));
        Long drainId = drain(other.getId(), "ADOPTED");
        Long adoptionId = adoption(
                other.getId(), drainId, cleaning(other.getId(), drainId), "ACTIVE");

        update(adoptionId, "새또랑이")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT nickname FROM adoption WHERE id = ?", String.class, adoptionId))
                .isEqualTo("또랑이");
    }

    @Test
    void 없는_입양이면_404() throws Exception {
        update(999_999L, "새또랑이")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ADOPTION_NOT_FOUND"));
    }

    @Test
    void 해제된_입양이면_404() throws Exception {
        Long drainId = drain(userId, "AVAILABLE");
        Long adoptionId = adoption(userId, drainId, cleaning(userId, drainId), "RELEASED");

        update(adoptionId, "새또랑이")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ADOPTION_NOT_FOUND"));
    }

    @Test
    void 빈_별명이면_400() throws Exception {
        Long drainId = drain(userId, "ADOPTED");
        Long adoptionId = adoption(userId, drainId, cleaning(userId, drainId), "ACTIVE");

        update(adoptionId, " ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("nickname"));
    }

    @Test
    void 별명이_20자를_넘으면_400() throws Exception {
        Long drainId = drain(userId, "ADOPTED");
        Long adoptionId = adoption(userId, drainId, cleaning(userId, drainId), "ACTIVE");

        update(adoptionId, "가".repeat(21))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("nickname"));
    }

    private ResultActions update(Long adoptionId, String nickname) throws Exception {
        return mockMvc.perform(patch("/api/v1/adoptions/{adoptionId}/nickname", adoptionId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(nickname)));
    }

    private String body(String nickname) {
        return "{\"nickname\":\"" + nickname + "\"}";
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

    private Long adoption(Long ownerId, Long drainId, Long cleaningId, String status) {
        return jdbcTemplate.queryForObject("INSERT INTO adoption (user_id, drain_id, "
                + "cleaning_id, nickname, status, level, score, ignore_count, adopted_at, "
                + "created_at, updated_at) VALUES (?, ?, ?, '또랑이', ?, 1, 0, 0, now(), "
                + "now(), now()) RETURNING id", Long.class, ownerId, drainId, cleaningId, status);
    }
}
