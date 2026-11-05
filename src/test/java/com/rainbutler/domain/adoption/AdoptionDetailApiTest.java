package com.rainbutler.domain.adoption;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rainbutler.domain.user.entity.Role;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.global.security.JwtTokenProvider;
import com.rainbutler.global.storage.StorageService;
import com.rainbutler.support.IntegrationTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 입양 상세의 권한 · 응답 필드 · 해제된 입양 처리를 검증합니다.
 */
@AutoConfigureMockMvc
class AdoptionDetailApiTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private StorageService storageService;

    private Long userId;
    private String token;

    @BeforeEach
    void setUp() {
        given(storageService.toReadUrl(anyString()))
                .willAnswer(invocation -> "http://" + invocation.getArgument(0));

        User user = userRepository.save(User.local(
                UUID.randomUUID() + "@test.com", "{noop}x", "입양집사"));
        userId = user.getId();
        token = jwtTokenProvider.createAccessToken(userId, user.getRole());
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        Long drainId = drain(userId, "ADOPTED");
        Long adoptionId = adoption(userId, drainId, cleaning(userId, drainId));

        mockMvc.perform(get("/api/v1/adoptions/{adoptionId}", adoptionId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void 본인_입양_상세를_조회한다() throws Exception {
        Long drainId = drain(userId, "ADOPTED");
        Long adoptionId = adoption(userId, drainId, cleaning(userId, drainId));

        mockMvc.perform(get("/api/v1/adoptions/{adoptionId}", adoptionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.adoptionId").value(adoptionId))
                .andExpect(jsonPath("$.data.nickname").value("또랑이"))
                .andExpect(jsonPath("$.data.adoptedAt").exists())
                .andExpect(jsonPath("$.data.level").value(2))
                .andExpect(jsonPath("$.data.score").value(60))
                .andExpect(jsonPath("$.data.levelTitle").value("새싹"))
                .andExpect(jsonPath("$.data.nextLevelScore").value(150))
                .andExpect(jsonPath("$.data.progressPercent").value(10))
                .andExpect(jsonPath("$.data.daysUntilNextManagement").value(12))
                .andExpect(jsonPath("$.data.releaseWarning").value(false))
                .andExpect(jsonPath("$.data.displayStatus").value("NORMAL"))
                .andExpect(jsonPath("$.data.displayStatusLabel").value("정상"))
                .andExpect(jsonPath("$.data.drain.drainId").value(drainId))
                .andExpect(jsonPath("$.data.drain.name").value("역곡역 앞"))
                .andExpect(jsonPath("$.data.drain.address").value("경기도 부천시 역곡로"))
                .andExpect(jsonPath("$.data.drain.latitude").value(37.5))
                .andExpect(jsonPath("$.data.drain.longitude").value(127.0))
                .andExpect(jsonPath("$.data.drain.photoUrl")
                        .value("http://drain-photo"))
                .andExpect(jsonPath("$.data.drain.dirtLevel").value(2))
                .andExpect(jsonPath("$.data.drain.risk").value(true));
    }

    @Test
    void 다음_관리일이_지나면_점검_지연이고_남은_일수는_음수다() throws Exception {
        Long drainId = drain(userId, "ADOPTED");
        Long adoptionId = adoption(userId, drainId, cleaning(userId, drainId));
        jdbcTemplate.update("UPDATE adoption SET next_management_at = now() - interval '3 days', "
                + "last_managed_at = now() - interval '31 days' WHERE id = ?", adoptionId);

        mockMvc.perform(get("/api/v1/adoptions/{adoptionId}", adoptionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayStatus").value("MANAGEMENT_DELAYED"))
                .andExpect(jsonPath("$.data.releaseWarning").value(true))
                .andExpect(jsonPath("$.data.daysUntilNextManagement").value(-3));
    }

    @Test
    void 다른_사람의_입양이면_403() throws Exception {
        User other = userRepository.save(User.local(
                UUID.randomUUID() + "@test.com", "{noop}x", "다른집사"));
        Long drainId = drain(other.getId(), "ADOPTED");
        Long adoptionId = adoption(other.getId(), drainId, cleaning(other.getId(), drainId));

        mockMvc.perform(get("/api/v1/adoptions/{adoptionId}", adoptionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void 관리자는_다른_사람의_입양_상세를_볼_수_있다() throws Exception {
        User other = userRepository.save(User.local(
                UUID.randomUUID() + "@test.com", "{noop}x", "다른집사"));
        Long drainId = drain(other.getId(), "ADOPTED");
        Long adoptionId = adoption(other.getId(), drainId, cleaning(other.getId(), drainId));
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", userId);
        String adminToken = jwtTokenProvider.createAccessToken(userId, Role.ADMIN);

        mockMvc.perform(get("/api/v1/adoptions/{adoptionId}", adoptionId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.adoptionId").value(adoptionId));
    }

    @Test
    void 없는_입양이면_404() throws Exception {
        mockMvc.perform(get("/api/v1/adoptions/{adoptionId}", 999_999L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ADOPTION_NOT_FOUND"));
    }

    @Test
    void 해제된_입양이면_404() throws Exception {
        Long drainId = drain(userId, "AVAILABLE");
        Long adoptionId = adoption(userId, drainId, cleaning(userId, drainId));
        jdbcTemplate.update("UPDATE adoption SET status = 'RELEASED', release_reason = 'MOVED', "
                + "released_at = now() WHERE id = ?", adoptionId);

        mockMvc.perform(get("/api/v1/adoptions/{adoptionId}", adoptionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ADOPTION_NOT_FOUND"));
    }

    private Long drain(Long registeredBy, String adoptionStatus) {
        return jdbcTemplate.queryForObject("INSERT INTO drain (registered_by, latitude, "
                + "longitude, name, address, photo_url, dirt_level, adoption_status, status, "
                + "is_risk, created_at, updated_at) VALUES (?, 37.5, 127.0, '역곡역 앞', "
                + "'경기도 부천시 역곡로', 'drain-photo', 2, ?, 'ACTIVE', true, now(), now()) "
                + "RETURNING id", Long.class, registeredBy, adoptionStatus);
    }

    private Long cleaning(Long ownerId, Long drainId) {
        return jdbcTemplate.queryForObject("INSERT INTO cleaning (user_id, drain_id, "
                + "type, before_photo_url, status, review_status, latitude, longitude, "
                + "location_mismatch, started_at, created_at, updated_at) VALUES (?, ?, "
                + "'ADOPTION', 'http://x', 'COMPLETED', 'NOT_REQUIRED', 37.5, 127.0, false, "
                + "now(), now(), now()) RETURNING id", Long.class, ownerId, drainId);
    }

    private Long adoption(Long ownerId, Long drainId, Long cleaningId) {
        return jdbcTemplate.queryForObject("INSERT INTO adoption (user_id, drain_id, "
                + "cleaning_id, nickname, status, level, score, ignore_count, adopted_at, "
                + "last_managed_at, next_management_at, created_at, updated_at) VALUES (?, ?, ?, "
                + "'또랑이', 'ACTIVE', 2, 60, 0, now() - interval '2 days', "
                + "now() - interval '2 days', now() + interval '12 days', now(), now()) "
                + "RETURNING id", Long.class, ownerId, drainId, cleaningId);
    }
}
