package com.rainbutler.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rainbutler.domain.adoption.entity.Adoption;
import com.rainbutler.domain.adoption.repository.AdoptionRepository;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.domain.user.service.PauseService;
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
 * 점검 쉬기 조회 · 신청과 관리 기준 시각을 검증합니다. (시각은 Clock으로 고정)
 */
@AutoConfigureMockMvc
class PauseServiceTest extends IntegrationTestSupport {

    private static final String PATH = "/api/v1/users/me/pause";
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 18, 10, 0);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PauseService pauseService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdoptionRepository adoptionRepository;

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
        setNow(T0);
        User onboarded = User.local(UUID.randomUUID() + "@test.com", "{noop}x", "쉬기집사");
        onboarded.completeOnboarding(T0, true, true);
        user = userRepository.save(onboarded);
        token = jwtTokenProvider.createAccessToken(user.getId(), user.getRole());
    }

    @Test
    void 신청하면_14일_쉬고_입양_다음_관리일이_쉬기_종료_뒤로_미뤄진다() throws Exception {
        Long soon = saveAdoption(T0.minusDays(20), T0.plusDays(3));
        Long later = saveAdoption(T0.minusDays(20), T0.plusDays(20));

        apply().andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pauseUntil").value("2026-11-01T10:00:00"));

        assertThat(nextManagementAt(soon)).isEqualTo(T0.plusDays(14));
        // 이미 더 늦은 예정일은 그대로 둔다
        assertThat(nextManagementAt(later)).isEqualTo(T0.plusDays(20));
        getStatus().andExpect(jsonPath("$.data.canApply").value(false))
                .andExpect(jsonPath("$.data.reason").value("ALREADY_ACTIVE"))
                .andExpect(jsonPath("$.data.activeUntil").value("2026-11-01T10:00:00"));
    }

    @Test
    void 입양이_없으면_409_PAUSE_NO_ADOPTION() throws Exception {
        getStatus().andExpect(jsonPath("$.data.reason").value("NO_ADOPTION"));
        apply().andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PAUSE_NO_ADOPTION"))
                .andExpect(jsonPath("$.error.details").doesNotExist());
    }

    @Test
    void 쉬는_중에_다시_신청하면_409_PAUSE_ALREADY_ACTIVE() throws Exception {
        saveAdoption(T0.minusDays(20), T0.plusDays(3));
        apply().andExpect(status().isOk());

        setNow(T0.plusDays(5));
        apply().andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PAUSE_ALREADY_ACTIVE"));
    }

    @Test
    void 쿨다운_중이면_409와_다음_신청_가능_시각을_주고_89일째_불가_90일째_가능() throws Exception {
        saveAdoption(T0.minusDays(20), T0.plusDays(3));
        apply().andExpect(status().isOk());

        setNow(T0.plusDays(89));
        getStatus().andExpect(jsonPath("$.data.canApply").value(false))
                .andExpect(jsonPath("$.data.reason").value("COOLDOWN"))
                .andExpect(jsonPath("$.data.nextAvailableAt").value("2027-01-16T10:00:00"));
        apply().andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PAUSE_COOLDOWN"))
                .andExpect(jsonPath("$.error.details.nextAvailableAt")
                        .value("2027-01-16T10:00:00"));

        setNow(T0.plusDays(90));
        getStatus().andExpect(jsonPath("$.data.canApply").value(true))
                .andExpect(jsonPath("$.data.reason").doesNotExist());
        apply().andExpect(status().isOk());
    }

    @Test
    void 온보딩을_마치지_않았으면_403_ONBOARDING_REQUIRED() throws Exception {
        User fresh = userRepository.save(User.local(
                UUID.randomUUID() + "@test.com", "{noop}x", "새집사"));
        String freshToken = jwtTokenProvider.createAccessToken(fresh.getId(), fresh.getRole());

        mockMvc.perform(post(PATH).header("Authorization", "Bearer " + freshToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ONBOARDING_REQUIRED"));
    }

    @Test
    void 관리_기준_시각은_쉬기_이력이_없으면_입양과_마지막_관리_중_늦은_시각() {
        Adoption adoption = adoption(saveAdoption(T0.minusDays(30), null, T0.minusDays(10)));

        assertThat(pauseService.careBaseline(adoption, reload())).isEqualTo(T0.minusDays(10));
        assertThat(pauseService.isCareSuspended(reload())).isFalse();
    }

    @Test
    void 쉬는_중에는_관리가_보류되고_기준에_쉬기_종료_시각을_넣지_않는다() throws Exception {
        Long id = saveAdoption(T0.minusDays(30), T0.plusDays(3), T0.minusDays(10));
        apply().andExpect(status().isOk());

        setNow(T0.plusDays(7));
        assertThat(pauseService.isCareSuspended(reload())).isTrue();
        assertThat(pauseService.careBaseline(adoption(id), reload())).isEqualTo(T0.minusDays(10));
    }

    @Test
    void 쉬기가_끝난_뒤에는_쉬기_종료_시각이_기준이_된다() throws Exception {
        Long id = saveAdoption(T0.minusDays(30), T0.plusDays(3), T0.minusDays(10));
        apply().andExpect(status().isOk());

        setNow(T0.plusDays(15));
        assertThat(pauseService.isCareSuspended(reload())).isFalse();
        assertThat(pauseService.careBaseline(adoption(id), reload())).isEqualTo(T0.plusDays(14));
    }

    private ResultActions apply() throws Exception {
        return mockMvc.perform(post(PATH).header("Authorization", "Bearer " + token));
    }

    private ResultActions getStatus() throws Exception {
        return mockMvc.perform(get(PATH).header("Authorization", "Bearer " + token));
    }

    private Long saveAdoption(LocalDateTime adoptedAt, LocalDateTime nextManagementAt) {
        return saveAdoption(adoptedAt, nextManagementAt, null);
    }

    /** 입양에 필요한 빗물받이 · 청소를 함께 만든다 */
    private Long saveAdoption(LocalDateTime adoptedAt, LocalDateTime nextManagementAt,
            LocalDateTime lastManagedAt) {
        Long drainId = jdbcTemplate.queryForObject("INSERT INTO drain (registered_by, latitude, "
                + "longitude, photo_url, dirt_level, adoption_status, status, is_risk, created_at, "
                + "updated_at) VALUES (?, 37.5, 127.0, 'http://x', 1, 'ADOPTED', 'ACTIVE', false, "
                + "now(), now()) RETURNING id", Long.class, user.getId());
        Long cleaningId = jdbcTemplate.queryForObject("INSERT INTO cleaning (user_id, drain_id, "
                + "type, before_photo_url, status, review_status, latitude, longitude, "
                + "location_mismatch, started_at, created_at, updated_at) "
                + "VALUES (?, ?, 'ADOPTION', 'http://x', 'COMPLETED', 'NOT_REQUIRED', "
                + "37.5, 127.0, false, now(), now(), now()) RETURNING id",
                Long.class, user.getId(), drainId);
        return jdbcTemplate.queryForObject("INSERT INTO adoption (user_id, drain_id, cleaning_id, "
                + "nickname, status, level, score, ignore_count, adopted_at, last_managed_at, "
                + "next_management_at, created_at, updated_at) VALUES (?, ?, ?, '또랑이', 'ACTIVE', "
                + "1, 0, 0, ?, ?, ?, now(), now()) RETURNING id", Long.class, user.getId(), drainId,
                cleaningId, ts(adoptedAt), ts(lastManagedAt), ts(nextManagementAt));
    }

    private LocalDateTime nextManagementAt(Long adoptionId) {
        return jdbcTemplate.queryForObject(
                "SELECT next_management_at FROM adoption WHERE id = ?",
                Timestamp.class, adoptionId).toLocalDateTime();
    }

    private Adoption adoption(Long id) {
        return adoptionRepository.findById(id).orElseThrow();
    }

    private User reload() {
        return userRepository.findById(user.getId()).orElseThrow();
    }

    private static Timestamp ts(LocalDateTime time) {
        return time == null ? null : Timestamp.valueOf(time);
    }

    private void setNow(LocalDateTime now) {
        given(clock.getZone()).willReturn(SEOUL);
        given(clock.instant()).willReturn(now.atZone(SEOUL).toInstant());
    }
}
