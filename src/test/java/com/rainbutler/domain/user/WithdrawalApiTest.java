package com.rainbutler.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.rainbutler.domain.auth.client.KakaoOAuthClient;
import com.rainbutler.domain.auth.client.KakaoUserInfo;
import com.rainbutler.domain.auth.dto.KakaoLoginResponse;
import com.rainbutler.domain.auth.service.KakaoAuthService;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.domain.user.service.WithdrawalService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 회원 탈퇴(소프트 삭제 + 익명화)와 입양 해제 · 요청 재공개 · 재가입을 검증합니다.
 */
@AutoConfigureMockMvc
class WithdrawalApiTest extends IntegrationTestSupport {

    private static final String ME = "/api/v1/users/me";
    private static final String REDIRECT_URI = "http://localhost:5173/oauth/kakao";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private WithdrawalService withdrawalService;

    @Autowired
    private KakaoAuthService kakaoAuthService;

    @MockitoBean
    private KakaoOAuthClient kakaoOAuthClient;

    private String email;
    private Long userId;
    private String accessToken;
    private String refreshToken;
    private Long forecastId;

    @BeforeEach
    void setUp() throws Exception {
        email = "w" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
        String body = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"rain1234\","
                                + "\"nickname\":\"탈퇴집사\"}"))
                .andReturn().getResponse().getContentAsString();
        accessToken = JsonPath.read(body, "$.data.accessToken");
        refreshToken = JsonPath.read(body, "$.data.refreshToken");
        userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ? AND deleted_at IS NULL", Long.class, email);
        forecastId = jdbcTemplate.queryForObject("INSERT INTO rain_forecast (rain_date, "
                + "rain_start_at, pop, pcp_mm, status, is_simulated, created_at, updated_at) "
                + "VALUES (current_date + 1, now() + interval '1 day', 80, 5.0, 'ACTIVE', false, "
                + "now(), now()) RETURNING id", Long.class);
    }

    @Test
    void 입양을_모두_해제하고_빗물받이를_입양_가능으로_돌리고_요청을_다시_공개한다() throws Exception {
        Long drain1 = saveAdoptedDrainWithRequest("SENT");
        Long drain2 = saveAdoptedDrainWithRequest("ACCEPTED");

        withdraw().andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));

        assertThat(jdbcTemplate.queryForList("SELECT status || '/' || release_reason FROM adoption "
                + "WHERE user_id = ?", String.class, userId))
                .containsOnly("RELEASED/WITHDRAWN").hasSize(2);
        assertThat(jdbcTemplate.queryForList("SELECT adoption_status FROM drain WHERE id IN (?, ?)",
                String.class, drain1, drain2)).containsOnly("AVAILABLE");
        assertThat(jdbcTemplate.queryForList("SELECT status FROM inspection_request "
                + "WHERE drain_id IN (?, ?) AND opened_at IS NOT NULL", String.class,
                drain1, drain2)).containsOnly("OPEN").hasSize(2);
    }

    @Test
    void 맡았던_대신_점검_요청은_다시_공개되고_수락자가_비워진다() throws Exception {
        Long otherDrain = saveDrain(saveKakaoUser("다른집사").getId(), "AVAILABLE");
        Long requestId = jdbcTemplate.queryForObject("INSERT INTO inspection_request (forecast_id, "
                + "drain_id, substitute_user_id, status, sent_at, reply_deadline_at, opened_at, "
                + "claimed_at, created_at, updated_at) VALUES (?, ?, ?, 'CLAIMED', now(), now(), "
                + "now(), now(), now(), now()) RETURNING id", Long.class, forecastId, otherDrain,
                userId);

        withdraw().andExpect(status().isOk());

        Map<String, Object> request = jdbcTemplate.queryForMap("SELECT status, substitute_user_id, "
                + "claimed_at FROM inspection_request WHERE id = ?", requestId);
        assertThat(request.get("status")).isEqualTo("OPEN");
        assertThat(request.get("substitute_user_id")).isNull();
        assertThat(request.get("claimed_at")).isNull();
    }

    @Test
    void 빌린_도구가_있으면_409이고_아무것도_바뀌지_않는다() throws Exception {
        saveAdoptedDrainWithRequest("SENT");
        Long toolBox = jdbcTemplate.queryForObject("INSERT INTO tool_box (name, latitude, "
                + "longitude, total_count, available_count, status, created_at, updated_at) "
                + "VALUES ('도구함', 37.5, 127.0, 3, 2, 'AVAILABLE', now(), now()) RETURNING id",
                Long.class);
        jdbcTemplate.update("INSERT INTO tool_rental (user_id, tool_box_id, status, rented_at, "
                + "created_at, updated_at) VALUES (?, ?, 'RENTED', now(), now(), now())",
                userId, toolBox);

        withdraw().andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("WITHDRAW_TOOL_NOT_RETURNED"));

        assertThat(jdbcTemplate.queryForObject("SELECT deleted_at IS NULL FROM users WHERE id = ?",
                Boolean.class, userId)).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM adoption WHERE user_id = ?",
                String.class, userId)).isEqualTo("ACTIVE");
    }

    @Test
    void 탈퇴하면_개인정보를_비우고_토큰을_정리한다() throws Exception {
        jdbcTemplate.update("INSERT INTO fcm_token (user_id, token, platform, last_used_at, "
                + "created_at, updated_at) VALUES (?, ?, 'WEB', now(), now(), now())",
                userId, "fcm-" + UUID.randomUUID());

        withdraw().andExpect(status().isOk());

        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT nickname, email, password, "
                + "provider_id, notification_enabled, deleted_at FROM users WHERE id = ?", userId);
        assertThat(row.get("nickname")).isEqualTo("탈퇴한 사용자");
        assertThat(row.get("email")).isNull();
        assertThat(row.get("password")).isNull();
        assertThat(row.get("notification_enabled")).isEqualTo(false);
        assertThat(row.get("deleted_at")).isNotNull();
        assertThat(count("fcm_token")).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM refresh_token "
                + "WHERE user_id = ? AND revoked_at IS NULL", Integer.class, userId)).isZero();
    }

    @Test
    void 탈퇴_후_refresh는_401_기존_access로_내_정보는_404() throws Exception {
        withdraw().andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
        mockMvc.perform(get(ME).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
    }

    @Test
    void 같은_이메일로_다시_가입하면_새_계정이다() throws Exception {
        withdraw().andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"rain1234\","
                                + "\"nickname\":\"다시집사\"}"))
                .andExpect(status().isCreated());

        Long newId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ? AND deleted_at IS NULL", Long.class, email);
        assertThat(newId).isNotEqualTo(userId);
    }

    @Test
    void 같은_카카오_계정으로_다시_오면_새_계정으로_가입된다() {
        String kakaoId = String.valueOf(Math.abs(UUID.randomUUID().getMostSignificantBits()));
        given(kakaoOAuthClient.fetchUser(anyString(), anyString()))
                .willReturn(new KakaoUserInfo(kakaoId, "카카오집사", null));
        KakaoLoginResponse first = kakaoAuthService.login("code", REDIRECT_URI);
        Long firstId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE provider_id = ?",
                Long.class, kakaoId);

        withdrawalService.withdraw(firstId);
        KakaoLoginResponse again = kakaoAuthService.login("code", REDIRECT_URI);

        assertThat(first.isNewUser()).isTrue();
        assertThat(again.isNewUser()).isTrue();
        Long secondId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE provider_id = ?",
                Long.class, kakaoId);
        assertThat(secondId).isNotEqualTo(firstId);
    }

    @Test
    void 탈퇴한_사용자가_등록한_빗물받이도_상세_조회된다() throws Exception {
        Long drain = saveAdoptedDrainWithRequest("SENT");
        withdraw().andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/drain/" + drain))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(drain))
                .andExpect(jsonPath("$.data.adoptionStatus").value("AVAILABLE"));
    }

    private ResultActions withdraw() throws Exception {
        return mockMvc.perform(delete(ME).header("Authorization", "Bearer " + accessToken));
    }

    /** 이 사용자가 등록 · 입양한 빗물받이와, 그 입양에 걸린 점검 요청을 만든다 */
    private Long saveAdoptedDrainWithRequest(String requestStatus) {
        Long drain = saveDrain(userId, "ADOPTED");
        Long cleaning = jdbcTemplate.queryForObject("INSERT INTO cleaning (user_id, drain_id, "
                + "type, before_photo_url, status, review_status, latitude, longitude, "
                + "location_mismatch, started_at, created_at, updated_at) VALUES (?, ?, "
                + "'ADOPTION', 'http://x', 'COMPLETED', 'NOT_REQUIRED', 37.5, 127.0, false, "
                + "now(), now(), now()) RETURNING id", Long.class, userId, drain);
        Long adoption = jdbcTemplate.queryForObject("INSERT INTO adoption (user_id, drain_id, "
                + "cleaning_id, nickname, status, level, score, ignore_count, adopted_at, "
                + "created_at, updated_at) VALUES (?, ?, ?, '또랑이', 'ACTIVE', 1, 0, 0, now(), "
                + "now(), now()) RETURNING id", Long.class, userId, drain, cleaning);
        jdbcTemplate.update("INSERT INTO inspection_request (forecast_id, drain_id, adoption_id, "
                + "status, sent_at, reply_deadline_at, created_at, updated_at) VALUES (?, ?, ?, ?, "
                + "now(), now() + interval '6 hour', now(), now())", forecastId, drain, adoption,
                requestStatus);
        return drain;
    }

    private Long saveDrain(Long registeredBy, String adoptionStatus) {
        return jdbcTemplate.queryForObject("INSERT INTO drain (registered_by, latitude, "
                + "longitude, photo_url, dirt_level, adoption_status, status, is_risk, "
                + "created_at, updated_at) VALUES (?, 37.5, 127.0, 'http://x', 1, ?, 'ACTIVE', "
                + "false, now(), now()) RETURNING id", Long.class, registeredBy, adoptionStatus);
    }

    private User saveKakaoUser(String nickname) {
        return userRepository.save(User.kakao(UUID.randomUUID().toString(), nickname, null));
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE user_id = ?", Integer.class, userId);
    }
}
