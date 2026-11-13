package com.rainbutler.domain.bugreport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.rainbutler.domain.user.entity.Role;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.security.JwtTokenProvider;
import com.rainbutler.global.storage.StorageService;
import com.rainbutler.global.storage.UploadPurpose;
import com.rainbutler.support.IntegrationTestSupport;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/**
 * 버그 신고 등록 · 내 목록 · 관리자 목록 · 상세 · 처리 API를 검증합니다.
 *
 * <p>S3는 mock이라 외부 연결 없이 동작합니다. 사진 key는 {@code test/bug_report/{userId}/...} 형식만 본인 것으로
 * 인정합니다.
 */
@AutoConfigureMockMvc
class BugReportApiTest extends IntegrationTestSupport {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String CONTENT = "점검 사진을 올리면 화면이 하얗게 멈춰요.";

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

    /** 온보딩을 마치지 않은 사용자 (버그 신고는 온보딩 없이 된다) */
    private Long userId;
    private String token;
    private Long adminId;
    private String adminToken;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.local(
                UUID.randomUUID() + "@test.com", "{noop}x", "신고자"));
        userId = user.getId();
        token = jwtTokenProvider.createAccessToken(userId, user.getRole());
        adminId = userRepository.save(User.local(
                UUID.randomUUID() + "@test.com", "{noop}x", "운영자")).getId();
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", adminId);
        adminToken = jwtTokenProvider.createAccessToken(adminId, Role.ADMIN);

        // 본인이 버그 신고 용도로 올린 key만 통과시킨다
        given(storageService.verifyUploaded(anyLong(), anyString(),
                eq(UploadPurpose.BUG_REPORT))).willAnswer(invocation -> {
                    String key = invocation.getArgument(1);
                    if (!key.startsWith("test/bug_report/" + invocation.getArgument(0) + "/")) {
                        throw new BusinessException(ErrorCode.PHOTO_FORBIDDEN);
                    }
                    return key;
                });
        given(storageService.toReadUrl(anyString()))
                .willAnswer(invocation -> "http://" + invocation.getArgument(0));
    }

    @Test
    void 온보딩_전이어도_신고하고_User_Agent와_사진을_저장한다() throws Exception {
        String photoKey = "test/bug_report/" + userId + "/a.jpg";
        Long id = create(token, Map.of("category", "ERROR", "content", "  " + CONTENT + "  ",
                "screen", "/inspections/new", "requestId", "ab12cd34", "appVersion", "1.0.3",
                "platform", "WEB", "photoKeys", List.of(photoKey)), "Mozilla/5.0 테스트")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RECEIVED"))
                .andReturn().getResponse().getContentAsString().transform(this::bugReportId);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT * FROM bug_report WHERE id = ?", id);
        assertThat(row.get("user_id")).isEqualTo(userId);
        assertThat(row.get("content")).isEqualTo(CONTENT);
        assertThat(row.get("user_agent")).isEqualTo("Mozilla/5.0 테스트");
        assertThat(row.get("photo_keys")).isEqualTo(photoKey);
        assertThat(row.get("platform")).isEqualTo("WEB");
    }

    @Test
    void User_Agent는_500자에서_자른다() throws Exception {
        Long id = create(token, body(CONTENT), "a".repeat(600))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString().transform(this::bugReportId);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT length(user_agent) FROM bug_report WHERE id = ?", Integer.class, id))
                .isEqualTo(500);
    }

    @Test
    void 내용_길이와_사진_개수를_검사한다() throws Exception {
        create(token, body("가".repeat(9)), null).andExpect(status().isBadRequest());
        // 앞뒤 공백은 길이에 넣지 않는다
        create(token, body("   " + "가".repeat(9) + "   "), null)
                .andExpect(status().isBadRequest());
        create(token, body("가".repeat(2001)), null).andExpect(status().isBadRequest());
        create(token, body("가".repeat(2000)), null).andExpect(status().isOk());

        String key = "test/bug_report/" + userId + "/a.jpg";
        create(token, Map.of("category", "UI", "content", CONTENT,
                "photoKeys", List.of(key, key, key, key)), null)
                .andExpect(status().isBadRequest());
        create(token, Map.of("content", CONTENT), null).andExpect(status().isBadRequest());
    }

    @Test
    void 남이_올린_사진_key는_거절한다() throws Exception {
        create(token, Map.of("category", "UI", "content", CONTENT,
                "photoKeys", List.of("test/bug_report/" + adminId + "/a.jpg")), null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PHOTO_FORBIDDEN"));
        assertThat(count(userId)).isZero();
    }

    @Test
    void 하루_10건을_넘으면_429() throws Exception {
        for (int i = 0; i < 10; i++) {
            create(token, body(CONTENT), null).andExpect(status().isOk());
        }

        create(token, body(CONTENT), null)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("BUG_REPORT_DAILY_LIMIT"));
        assertThat(count(userId)).isEqualTo(10);
    }

    @Test
    void 내_목록은_본인_것만_최신순이고_관리자_메모는_없다() throws Exception {
        Long first = createId(token, CONTENT + " 첫 번째");
        Long second = createId(token, "가".repeat(150));
        String otherToken = jwtTokenProvider.createAccessToken(adminId, Role.USER);
        createId(otherToken, CONTENT);
        mockMvc.perform(patch("/api/v1/admin/bug-reports/" + first)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\",\"adminNote\":\"비공개 메모\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/bug-reports/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.content[0].bugReportId").value(second))
                .andExpect(jsonPath("$.data.content[0].content").value("가".repeat(100) + "…"))
                .andExpect(jsonPath("$.data.content[1].bugReportId").value(first))
                .andExpect(jsonPath("$.data.content[1].status").value("RESOLVED"))
                .andExpect(jsonPath("$.data.content[1].resolvedAt").exists())
                .andExpect(jsonPath("$.data.content[1].adminNote").doesNotExist());
    }

    @Test
    void 관리자_API는_ADMIN만_쓴다() throws Exception {
        Long id = createId(token, CONTENT);
        String cityToken = jwtTokenProvider.createAccessToken(adminId, Role.CITY_OFFICIAL);

        for (String t : List.of(cityToken, token)) {
            mockMvc.perform(get("/api/v1/admin/bug-reports")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + t))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/admin/bug-reports/" + id)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + t))
                    .andExpect(status().isForbidden());
            mockMvc.perform(patch("/api/v1/admin/bug-reports/" + id)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + t)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"RESOLVED\"}"))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/v1/admin/bug-reports/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reporterNickname").value("신고자"));
        mockMvc.perform(get("/api/v1/admin/bug-reports/999999999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("BUG_REPORT_NOT_FOUND"));
    }

    @Test
    void 관리자_목록은_상태_분류로_거르고_탈퇴한_신고자를_표시한다() throws Exception {
        Long received = createId(token, CONTENT);
        Long inProgress = createId(token, CONTENT);
        mockMvc.perform(patch("/api/v1/admin/bug-reports/" + inProgress)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk());
        // 신고자 탈퇴
        jdbcTemplate.update("UPDATE users SET nickname = '탈퇴한 사용자', deleted_at = now() "
                + "WHERE id = ?", userId);

        String json = mockMvc.perform(get("/api/v1/admin/bug-reports")
                        .param("status", "IN_PROGRESS").param("size", "100")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(json, "$.data.content[*].bugReportId");
        List<String> statuses = JsonPath.read(json, "$.data.content[*].status");
        assertThat(ids).contains(inProgress.intValue()).doesNotContain(received.intValue());
        assertThat(statuses).containsOnly("IN_PROGRESS");
        assertThat(JsonPath.<List<String>>read(json, "$.data.content[?(@.bugReportId == "
                + inProgress + ")].reporterNickname")).containsExactly("탈퇴한 사용자");

        mockMvc.perform(get("/api/v1/admin/bug-reports")
                        .param("category", "FEATURE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].category").value(everyItem(is("FEATURE"))));
    }

    @Test
    void 해결하면_처리자와_시각을_남기고_다시_열면_지운다() throws Exception {
        String photoKey = "test/bug_report/" + userId + "/a.jpg";
        Long id = create(token, Map.of("category", "ERROR", "content", CONTENT,
                "photoKeys", List.of(photoKey)), null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString().transform(this::bugReportId);

        update(id, "{\"status\":\"RESOLVED\",\"adminNote\":\"1.0.4에서 수정\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"))
                .andExpect(jsonPath("$.data.adminNote").value("1.0.4에서 수정"))
                .andExpect(jsonPath("$.data.resolvedByNickname").value("운영자"))
                .andExpect(jsonPath("$.data.resolvedAt").exists())
                .andExpect(jsonPath("$.data.photoUrls[0]").value("http://" + photoKey));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT resolved_by FROM bug_report WHERE id = ?", Long.class, id))
                .isEqualTo(adminId);

        // 메모만 바꾸면 상태 · 처리자는 그대로
        update(id, "{\"adminNote\":\"배포 완료\"}")
                .andExpect(jsonPath("$.data.status").value("RESOLVED"))
                .andExpect(jsonPath("$.data.adminNote").value("배포 완료"))
                .andExpect(jsonPath("$.data.resolvedByNickname").value("운영자"));

        update(id, "{\"status\":\"RECEIVED\"}")
                .andExpect(jsonPath("$.data.status").value("RECEIVED"))
                .andExpect(jsonPath("$.data.adminNote").value("배포 완료"))
                .andExpect(jsonPath("$.data.resolvedByNickname").doesNotExist())
                .andExpect(jsonPath("$.data.resolvedAt").doesNotExist());
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT resolved_by, resolved_at FROM bug_report WHERE id = ?", id);
        assertThat(row.get("resolved_by")).isNull();
        assertThat(row.get("resolved_at")).isNull();

        update(id, "{\"adminNote\":\"" + "가".repeat(1001) + "\"}")
                .andExpect(status().isBadRequest());
        update(999_999_999L, "{\"status\":\"RESOLVED\"}").andExpect(status().isNotFound());
    }

    private ResultActions create(String bearer, Map<String, Object> body, String userAgent)
            throws Exception {
        var request = post("/api/v1/bug-reports")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(body));
        if (userAgent != null) {
            request.header(HttpHeaders.USER_AGENT, userAgent);
        }
        return mockMvc.perform(request);
    }

    private Long createId(String bearer, String content) throws Exception {
        return create(bearer, body(content), null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString().transform(this::bugReportId);
    }

    private ResultActions update(Long id, String json) throws Exception {
        return mockMvc.perform(patch("/api/v1/admin/bug-reports/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private static Map<String, Object> body(String content) {
        return Map.of("category", "ERROR", "content", content);
    }

    private Long bugReportId(String json) {
        return ((Number) JsonPath.read(json, "$.data.bugReportId")).longValue();
    }

    private int count(Long reporterId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bug_report WHERE user_id = ?", Integer.class, reporterId);
    }
}
