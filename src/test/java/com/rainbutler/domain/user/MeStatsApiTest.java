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
 * 내 정보의 참여 점수 · 입양 수 · 제재 · 쉬기 상태 · 점검 수 · 봉사 시간을 검증합니다. (시각은 Clock으로 고정)
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
                .andExpect(jsonPath("$.data.pause.nextAvailableAt").doesNotExist())
                .andExpect(jsonPath("$.data.inspectionCount").value(0))
                .andExpect(jsonPath("$.data.volunteerMinutes").value(0));
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

    @Test
    void 입양_전_청소는_봉사_시간에만_들어간다() throws Exception {
        cleaning(user.getId(), "ADOPTION", "COMPLETED", "PENDING");
        cleaning(user.getId(), "ADOPTION", "COMPLETED", "NOT_REQUIRED");

        me().andExpect(jsonPath("$.data.inspectionCount").value(0))
                .andExpect(jsonPath("$.data.volunteerMinutes").value(30));
    }

    @Test
    void 대신_점검은_점검_수와_봉사_시간에_모두_들어간다() throws Exception {
        cleaning(user.getId(), "SUBSTITUTE", "COMPLETED", "APPROVED");

        me().andExpect(jsonPath("$.data.inspectionCount").value(1))
                .andExpect(jsonPath("$.data.volunteerMinutes").value(15));
    }

    @Test
    void 입양_후_점검은_모두_세고_청소까지_한_것만_봉사_시간에_들어간다() throws Exception {
        Long adoption = adoption("ACTIVE");
        management(adoption, "INSPECTION", "VERIFIED", "NOT_REQUIRED");
        management(adoption, "CLEANING", "VERIFIED", "PENDING");

        // 입양 때 만든 입양 전 청소 1회 + 점검 중 청소 1회
        me().andExpect(jsonPath("$.data.inspectionCount").value(2))
                .andExpect(jsonPath("$.data.volunteerMinutes").value(30));
    }

    @Test
    void 해제한_입양의_점검_기록도_센다() throws Exception {
        Long adoption = adoption("RELEASED");
        management(adoption, "CLEANING", "VERIFIED", "APPROVED");

        me().andExpect(jsonPath("$.data.inspectionCount").value(1))
                .andExpect(jsonPath("$.data.volunteerMinutes").value(30));
    }

    @Test
    void 인증되지_않았거나_반려된_기록은_세지_않는다() throws Exception {
        cleaning(user.getId(), "SUBSTITUTE", "IN_PROGRESS", "PENDING");
        cleaning(user.getId(), "SUBSTITUTE", "FAILED", "NOT_REQUIRED");
        cleaning(user.getId(), "SUBSTITUTE", "COMPLETED", "REJECTED");
        Long adoption = adoption("ACTIVE");
        management(adoption, "CLEANING", "SUBMITTED", "PENDING");
        management(adoption, "CLEANING", "FAILED", "NOT_REQUIRED");
        management(adoption, "CLEANING", "VERIFIED", "REJECTED");

        // 입양 때 만든 입양 전 청소 1회만 남는다
        me().andExpect(jsonPath("$.data.inspectionCount").value(0))
                .andExpect(jsonPath("$.data.volunteerMinutes").value(15));
    }

    @Test
    void 다른_사용자의_기록은_세지_않는다() throws Exception {
        User other = userRepository.save(User.local(
                UUID.randomUUID() + "@test.com", "{noop}x", "다른집사"));
        cleaning(other.getId(), "SUBSTITUTE", "COMPLETED", "APPROVED");

        me().andExpect(jsonPath("$.data.inspectionCount").value(0))
                .andExpect(jsonPath("$.data.volunteerMinutes").value(0));
    }

    private ResultActions me() throws Exception {
        return mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token));
    }

    private void point(int amount, String type) {
        jdbcTemplate.update("INSERT INTO point_history (user_id, source_type, source_id, amount, "
                + "type, created_at, updated_at) VALUES (?, 'CLEANING', 1, ?, ?, now(), now())",
                user.getId(), amount, type);
    }

    /**
     * 입양을 만듭니다. 입양 전 청소(완료) 1건이 함께 생깁니다.
     */
    private Long adoption(String status) {
        Long drain = drain();
        Long cleaning = cleaning(user.getId(), drain, "ADOPTION", "COMPLETED", "NOT_REQUIRED");
        return jdbcTemplate.queryForObject("INSERT INTO adoption (user_id, drain_id, "
                + "cleaning_id, nickname, status, level, score, ignore_count, adopted_at, "
                + "created_at, updated_at) VALUES (?, ?, ?, '또랑이', ?, 1, 0, 0, now(), now(), "
                + "now()) RETURNING id", Long.class, user.getId(), drain, cleaning, status);
    }

    private Long drain() {
        return jdbcTemplate.queryForObject("INSERT INTO drain (registered_by, latitude, "
                + "longitude, photo_url, dirt_level, adoption_status, status, is_risk, "
                + "created_at, updated_at) VALUES (?, 37.5, 127.0, 'http://x', 1, 'ADOPTED', "
                + "'ACTIVE', false, now(), now()) RETURNING id", Long.class, user.getId());
    }

    private Long cleaning(Long userId, String type, String status, String reviewStatus) {
        return cleaning(userId, drain(), type, status, reviewStatus);
    }

    private Long cleaning(
            Long userId, Long drain, String type, String status, String reviewStatus) {
        return jdbcTemplate.queryForObject("INSERT INTO cleaning (user_id, drain_id, type, "
                + "before_photo_url, status, review_status, latitude, longitude, "
                + "location_mismatch, started_at, created_at, updated_at) VALUES (?, ?, ?, "
                + "'http://x', ?, ?, 37.5, 127.0, false, now(), now(), now()) RETURNING id",
                Long.class, userId, drain, type, status, reviewStatus);
    }

    private void management(
            Long adoption, String actionType, String status, String reviewStatus) {
        jdbcTemplate.update("INSERT INTO management_log (adoption_id, type, action_type, "
                + "status, review_status, location_mismatch, created_at, updated_at) "
                + "VALUES (?, 'REGULAR', ?, ?, ?, false, now(), now())",
                adoption, actionType, status, reviewStatus);
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
