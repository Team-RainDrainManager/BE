package com.rainbutler.domain.drain;

import static org.hamcrest.Matchers.hasSize;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 지도 · 목록 · 상세가 이름 · 주소 · 표시 상태 · 레벨을 함께 내려주는지 검증합니다.
 */
@AutoConfigureMockMvc
class DrainListApiTest extends IntegrationTestSupport {

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
                UUID.randomUUID() + "@test.com", "{noop}x", "목록집사"));
        userId = user.getId();
        token = jwtTokenProvider.createAccessToken(userId, user.getRole());
    }

    @Test
    void 지도_목록에_이름_주소_표시상태_레벨을_내려준다() throws Exception {
        Long available = drain("골목 친구", "부천시 원미구 역곡동", "AVAILABLE");
        Long normal = drain("등굣길 모퉁이", "부천시 원미구 지봉로", "ADOPTED");
        adoption(normal, 3, 150, "now() + interval '5 days'");

        mockMvc.perform(map())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[*].name").exists())
                .andExpect(jsonPath("$.data[*].address").exists())
                .andExpect(jsonPath("$.data[*].status").exists())
                .andExpect(jsonPath("$.data[0].status").value("unadopted"))
                .andExpect(jsonPath("$.data[1].status").value("normal"))
                .andExpect(jsonPath("$.data[*].displayStatus").exists())
                .andExpect(jsonPath("$.data[*].level").exists())
                .andExpect(jsonPath("$.data[*].levelTitle").exists())
                .andExpect(jsonPath("$.data[*].xp").exists());
    }

    @Test
    void 표시상태로_거른다() throws Exception {
        drain("미입양 또랑", null, "AVAILABLE");
        Long blocked = drain("막힌 또랑", null, "AVAILABLE");
        blockageReport(blocked);

        mockMvc.perform(map().param("status", "blocked"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(blocked.intValue()))
                .andExpect(jsonPath("$.data[0].status").value("blocked"))
                .andExpect(jsonPath("$.data[0].displayStatus").value("BLOCKAGE_REPORTED"));

        // 한글 라벨과 enum 이름도 같은 상태로 받는다
        mockMvc.perform(map().param("status", "막힘 제보"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
        mockMvc.perform(map().param("status", "BLOCKAGE_REPORTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    void 점검일이_지난_입양은_overdue다() throws Exception {
        Long delayed = drain("늦은 또랑", null, "ADOPTED");
        adoption(delayed, 2, 100, "now() - interval '1 day'");

        mockMvc.perform(map())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("overdue"))
                .andExpect(jsonPath("$.data[0].displayStatus")
                        .value("MANAGEMENT_DELAYED"));
    }

    @Test
    void 이름과_주소로_검색하고_페이지로_나눈다() throws Exception {
        drain("역곡동 골목 1번", "부천시 원미구 역곡동", "AVAILABLE");
        drain("역곡동 골목 2번", "부천시 원미구 역곡동", "AVAILABLE");
        drain("다른 동네", "서울시 강남구", "AVAILABLE");

        mockMvc.perform(get("/api/v1/drain/list")
                        .param("page", "0")
                        .param("size", "1")
                        .param("q", "역곡동"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(true));
    }

    @Test
    void 상세에서_내_빗물받이인지_알려준다() throws Exception {
        Long drainId = drain("내 또랑", "부천시", "ADOPTED");
        adoption(drainId, 2, 100, "now() + interval '5 days'");

        mockMvc.perform(get("/api/v1/drain/{drainId}", drainId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isMine").value(true))
                .andExpect(jsonPath("$.data.name").value("또랑이"))
                .andExpect(jsonPath("$.data.nextLevelScore").value(103))
                .andExpect(jsonPath("$.data.progressPercent").value(94));

        mockMvc.perform(get("/api/v1/drain/{drainId}", drainId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isMine").value(false));
    }

    @Test
    void 잘못된_표시상태는_400() throws Exception {
        mockMvc.perform(map().param("status", "NOPE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    private MockHttpServletRequestBuilder map() {
        return get("/api/v1/drain")
                .param("minLatitude", "37.0")
                .param("maxLatitude", "38.0")
                .param("minLongitude", "126.0")
                .param("maxLongitude", "127.0");
    }

    private Long drain(String name, String address, String adoptionStatus) {
        return jdbcTemplate.queryForObject("INSERT INTO drain (registered_by, latitude, "
                + "longitude, name, address, photo_url, dirt_level, adoption_status, status, "
                + "is_risk, created_at, updated_at) VALUES (?, 37.5, 127.0, ?, ?, 'http://x', "
                + "1, ?, 'ACTIVE', false, now(), now()) RETURNING id", Long.class, userId, name,
                address, adoptionStatus);
    }

    private Long adoption(Long drainId, int level, int score, String nextManagementAt) {
        Long cleaningId = jdbcTemplate.queryForObject("INSERT INTO cleaning (user_id, drain_id, "
                + "type, before_photo_url, status, review_status, latitude, longitude, "
                + "location_mismatch, started_at, created_at, updated_at) VALUES (?, ?, "
                + "'ADOPTION', 'http://x', 'COMPLETED', 'NOT_REQUIRED', 37.5, 127.0, false, "
                + "now(), now(), now()) RETURNING id", Long.class, userId, drainId);
        return jdbcTemplate.queryForObject("INSERT INTO adoption (user_id, drain_id, "
                + "cleaning_id, nickname, status, level, score, ignore_count, adopted_at, "
                + "next_management_at, created_at, updated_at) VALUES (?, ?, ?, '또랑이', "
                + "'ACTIVE', ?, ?, 0, now(), " + nextManagementAt + ", now(), now()) RETURNING id",
                Long.class, userId, drainId, cleaningId, level, score);
    }

    private void blockageReport(Long drainId) {
        jdbcTemplate.update("INSERT INTO report (user_id, drain_id, type, status, "
                + "created_at, updated_at) VALUES (?, ?, 'BLOCKAGE', 'PENDING', now(), now())",
                userId, drainId);
    }
}
