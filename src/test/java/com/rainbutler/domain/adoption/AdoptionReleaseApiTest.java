package com.rainbutler.domain.adoption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.global.security.JwtTokenProvider;
import com.rainbutler.support.IntegrationTestSupport;
import java.time.LocalDateTime;
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
 * 입양 해제(파양)의 권한 · 중복 · 요청 재공개 · 재입양 초기화를 검증합니다.
 */
@AutoConfigureMockMvc
class AdoptionReleaseApiTest extends IntegrationTestSupport {

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
    private Long forecastId;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.local(
                UUID.randomUUID() + "@test.com", "{noop}x", "파양집사"));
        userId = user.getId();
        token = jwtTokenProvider.createAccessToken(userId, user.getRole());

        // 같은 날짜의 진행 중 예보는 하나뿐이라(uq_rain_forecast_active) 이미 있으면 그 예보를 쓴다
        forecastId = jdbcTemplate.queryForObject("INSERT INTO rain_forecast (rain_date, "
                + "rain_start_at, pop, pcp_mm, status, is_simulated, created_at, updated_at) "
                + "VALUES (current_date + 1, now() + interval '1 day', 80, 5.0, 'ACTIVE', false, "
                + "now(), now()) ON CONFLICT (rain_date, is_simulated) "
                + "WHERE status = 'ACTIVE' AND deleted_at IS NULL "
                + "DO UPDATE SET updated_at = now() RETURNING id", Long.class);
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        Long drainId = drain(userId, "ADOPTED");
        Long adoptionId = adoption(userId, drainId, cleaning(userId, drainId), 1, 0);

        mockMvc.perform(post("/api/v1/adoptions/{adoptionId}/release", adoptionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"MOVED\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void 본인_입양을_해제하고_빗물받이를_입양_가능으로_돌린다() throws Exception {
        Long drainId = drain(userId, "ADOPTED");
        Long adoptionId = adoption(userId, drainId, cleaning(userId, drainId), 1, 0);

        release(adoptionId, "MOVED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.adoptionId").value(adoptionId))
                .andExpect(jsonPath("$.data.releasedAt").exists());

        Map<String, Object> adoption = jdbcTemplate.queryForMap(
                "SELECT status, release_reason, released_at FROM adoption WHERE id = ?",
                adoptionId);
        assertThat(adoption.get("status")).isEqualTo("RELEASED");
        assertThat(adoption.get("release_reason")).isEqualTo("MOVED");
        assertThat(adoption.get("released_at")).isNotNull();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT adoption_status FROM drain WHERE id = ?", String.class, drainId))
                .isEqualTo("AVAILABLE");
    }

    @Test
    void SENT_요청은_소유자를_비우고_OPEN으로_재공개한다() throws Exception {
        Long drainId = drain(userId, "ADOPTED");
        Long adoptionId = adoption(userId, drainId, cleaning(userId, drainId), 1, 0);
        Long requestId = request(adoptionId, drainId, "SENT", null, false);

        release(adoptionId, "NO_TIME").andExpect(status().isOk());

        Map<String, Object> request = jdbcTemplate.queryForMap("SELECT status, adoption_id, "
                + "reply, replied_at, opened_at FROM inspection_request WHERE id = ?", requestId);
        assertThat(request.get("status")).isEqualTo("OPEN");
        assertThat(request.get("adoption_id")).isNull();
        assertThat(request.get("reply")).isNull();
        assertThat(request.get("replied_at")).isNull();
        assertThat(request.get("opened_at")).isNotNull();
    }

    @Test
    void ACCEPTED_요청은_소유자와_응답을_비우고_OPEN으로_재공개한다() throws Exception {
        Long drainId = drain(userId, "ADOPTED");
        Long adoptionId = adoption(userId, drainId, cleaning(userId, drainId), 1, 0);
        Long requestId = request(adoptionId, drainId, "ACCEPTED", "ACCEPT", true);

        release(adoptionId, "OTHER").andExpect(status().isOk());

        Map<String, Object> request = jdbcTemplate.queryForMap("SELECT status, adoption_id, "
                + "reply, replied_at, opened_at FROM inspection_request WHERE id = ?", requestId);
        assertThat(request.get("status")).isEqualTo("OPEN");
        assertThat(request.get("adoption_id")).isNull();
        assertThat(request.get("reply")).isNull();
        assertThat(request.get("replied_at")).isNull();
        assertThat(request.get("opened_at")).isNotNull();
    }

    @Test
    void CLAIMED_요청은_그대로_둔다() throws Exception {
        Long drainId = drain(userId, "ADOPTED");
        Long adoptionId = adoption(userId, drainId, cleaning(userId, drainId), 1, 0);
        Long requestId = request(adoptionId, drainId, "CLAIMED", null, false);

        release(adoptionId, "MOVED").andExpect(status().isOk());

        Map<String, Object> request = jdbcTemplate.queryForMap(
                "SELECT status, adoption_id FROM inspection_request WHERE id = ?", requestId);
        assertThat(request.get("status")).isEqualTo("CLAIMED");
        assertThat(request.get("adoption_id")).isEqualTo(adoptionId);
    }

    @Test
    void 다른_사람의_입양이면_403() throws Exception {
        User other = userRepository.save(User.local(
                UUID.randomUUID() + "@test.com", "{noop}x", "다른집사"));
        Long drainId = drain(other.getId(), "ADOPTED");
        Long adoptionId = adoption(other.getId(), drainId,
                cleaning(other.getId(), drainId), 1, 0);

        release(adoptionId, "MOVED")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM adoption WHERE id = ?", String.class, adoptionId))
                .isEqualTo("ACTIVE");
    }

    @Test
    void 없는_입양이면_404() throws Exception {
        release(999_999L, "MOVED")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ADOPTION_NOT_FOUND"));
    }

    @Test
    void 이미_해제된_입양이면_409() throws Exception {
        Long drainId = drain(userId, "AVAILABLE");
        Long adoptionId = adoption(userId, drainId, cleaning(userId, drainId), 1, 0);
        jdbcTemplate.update("UPDATE adoption SET status = 'RELEASED', release_reason = 'MOVED', "
                + "released_at = now() WHERE id = ?", adoptionId);

        release(adoptionId, "NO_TIME")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    void 사용자가_고를_수_없는_사유면_400() throws Exception {
        Long drainId = drain(userId, "ADOPTED");
        Long adoptionId = adoption(userId, drainId, cleaning(userId, drainId), 1, 0);

        release(adoptionId, "AUTO")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void 다시_입양하면_Lv1_0점으로_시작하고_기존_기록은_남는다() throws Exception {
        Long drainId = drain(userId, "ADOPTED");
        Long oldAdoptionId = adoption(userId, drainId, cleaning(userId, drainId), 5, 900);

        release(oldAdoptionId, "MOVED").andExpect(status().isOk());

        Map<String, Object> oldAdoption = jdbcTemplate.queryForMap(
                "SELECT status, level, score FROM adoption WHERE id = ?", oldAdoptionId);
        assertThat(oldAdoption.get("status")).isEqualTo("RELEASED");
        assertThat(oldAdoption.get("level")).isEqualTo(5);
        assertThat(oldAdoption.get("score")).isEqualTo(900);

        User newOwner = userRepository.save(User.local(
                UUID.randomUUID() + "@test.com", "{noop}x", "새집사"));
        String newToken = jwtTokenProvider.createAccessToken(
                newOwner.getId(), newOwner.getRole());
        Long newCleaningId = cleaning(newOwner.getId(), drainId);

        String body = mockMvc.perform(post("/api/v1/drains/{drainId}/adoptions", drainId)
                        .header("Authorization", "Bearer " + newToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cleaningId\":" + newCleaningId + ",\"nickname\":\"새또랑이\","
                                + "\"safetyAgreed\":true}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long newAdoptionId = ((Number) JsonPath.read(body, "$.data.adoptionId")).longValue();

        Map<String, Object> newAdoption = jdbcTemplate.queryForMap(
                "SELECT status, level, score FROM adoption WHERE id = ?", newAdoptionId);
        assertThat(newAdoption.get("status")).isEqualTo("ACTIVE");
        assertThat(newAdoption.get("level")).isEqualTo(1);
        assertThat(newAdoption.get("score")).isEqualTo(0);

        mockMvc.perform(get("/api/v1/drain/{drainId}/growth", drainId)
                        .header("Authorization", "Bearer " + newToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.level").value(1))
                .andExpect(jsonPath("$.data.score").value(0));
    }

    private ResultActions release(Long adoptionId, String reason) throws Exception {
        return mockMvc.perform(post("/api/v1/adoptions/{adoptionId}/release", adoptionId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"" + reason + "\"}"));
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

    private Long adoption(Long ownerId, Long drainId, Long cleaningId, int level, int score) {
        return jdbcTemplate.queryForObject("INSERT INTO adoption (user_id, drain_id, "
                + "cleaning_id, nickname, status, level, score, ignore_count, adopted_at, "
                + "created_at, updated_at) VALUES (?, ?, ?, '또랑이', 'ACTIVE', ?, ?, 0, now(), "
                + "now(), now()) RETURNING id", Long.class, ownerId, drainId, cleaningId, level,
                score);
    }

    private Long request(Long adoptionId, Long drainId, String status, String reply,
            boolean replied) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime claimedAt = "CLAIMED".equals(status) ? now : null;
        return jdbcTemplate.queryForObject("INSERT INTO inspection_request (forecast_id, "
                + "drain_id, adoption_id, status, reply, sent_at, reply_deadline_at, "
                + "complete_deadline_at, replied_at, opened_at, claimed_at, created_at, "
                + "updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
                Long.class, forecastId, drainId, adoptionId, status, reply, now,
                now.plusHours(6), now.plusHours(14), replied ? now : null, claimedAt,
                claimedAt, now, now);
    }
}
