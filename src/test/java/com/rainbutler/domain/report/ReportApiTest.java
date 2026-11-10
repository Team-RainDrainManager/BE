package com.rainbutler.domain.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.rainbutler.domain.drain.service.DrainAiClassifier;
import com.rainbutler.domain.user.entity.Role;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 제보 생성 · 내 목록 · 상세 · 관리자 목록 · 승인/반려 API를 검증합니다.
 *
 * <p>S3는 mock이라 외부 연결 없이 동작합니다.
 */
@AutoConfigureMockMvc
class ReportApiTest extends IntegrationTestSupport {

    private static final double LAT = 37.5;
    private static final double LNG = 127.0;

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

    @MockitoBean
    private DrainAiClassifier drainAiClassifier;

    private Long userId;
    private String token;
    private Long drainId;
    private String photoKey;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.local(
                UUID.randomUUID() + "@test.com", "{noop}x", "제보자"));
        userId = user.getId();
        token = jwtTokenProvider.createAccessToken(userId, user.getRole());
        drainId = drain();
        photoKey = "test/report/" + userId + "/photo.jpg";

        given(storageService.verifyUploaded(anyLong(), anyString(), any(UploadPurpose.class)))
                .willAnswer(invocation -> invocation.getArgument(1));
        given(storageService.toReadUrl(anyString()))
                .willAnswer(invocation -> "http://" + invocation.getArgument(0));
        // 기본값: 막힘 제보 사진은 AI가 청소 불가로 판정한다.
        given(drainAiClassifier.assess(anyString()))
                .willReturn(new DrainAiClassifier.Assessment(3, true, 0.9, "판정", 3, true));
    }

    @Test
    void 제보를_생성하고_내_목록과_상세에서_조회한다() throws Exception {
        Long reportId = createReport("BLOCKAGE", photoKey, "덮개가 막혀 있어요", null, null);

        mockMvc.perform(get("/api/v1/reports/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reports", hasSize(1)))
                .andExpect(jsonPath("$.data.reports[0].reportId").value(reportId))
                .andExpect(jsonPath("$.data.reports[0].type").value("BLOCKAGE"))
                .andExpect(jsonPath("$.data.reports[0].status").value("PENDING"));

        mockMvc.perform(get("/api/v1/reports/{reportId}", reportId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reportId").value(reportId))
                .andExpect(jsonPath("$.data.drainId").value(drainId))
                .andExpect(jsonPath("$.data.type").value("BLOCKAGE"))
                .andExpect(jsonPath("$.data.description").value("덮개가 막혀 있어요"))
                .andExpect(jsonPath("$.data.photoUrl").value("http://" + photoKey))
                .andExpect(jsonPath("$.data.latitude").value(LAT))
                .andExpect(jsonPath("$.data.longitude").value(LNG))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.blocksAdoption").value(true))
                .andExpect(jsonPath("$.data.adminComment").value(nullValue()))
                .andExpect(jsonPath("$.data.createdAt").exists());

        Map<String, Object> report = jdbcTemplate.queryForMap(
                "SELECT type, status, blocks_adoption, photo_url, latitude, longitude "
                        + "FROM report WHERE id = ?",
                reportId);
        assertThat(report.get("type")).isEqualTo("BLOCKAGE");
        assertThat(report.get("status")).isEqualTo("PENDING");
        assertThat(report.get("blocks_adoption")).isEqualTo(true);
        assertThat(report.get("photo_url")).isEqualTo(photoKey);
        assertThat(((Number) report.get("latitude")).doubleValue()).isEqualTo(LAT);
        assertThat(((Number) report.get("longitude")).doubleValue()).isEqualTo(LNG);
    }

    @Test
    void 청소_불가가_아닌_막힘_제보는_거부한다() throws Exception {
        given(drainAiClassifier.assess(anyString()))
                .willReturn(new DrainAiClassifier.Assessment(1, false, 0.9, "깨끗함", 0, false));

        create("BLOCKAGE", photoKey, "막혔어요", null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("REPORT_NOT_UNCLEANABLE"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM report", Integer.class)).isZero();
    }

    @Test
    void 이미_입양을_막는_제보가_있으면_중복_접수하지_않는다() throws Exception {
        createReport("BLOCKAGE", photoKey, "막혔어요", null, null);

        create("BLOCKAGE", photoKey, "또 막혔어요", null, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REPORT_ALREADY_BLOCKED"));
    }

    @Test
    void 승인된_막힘_제보는_관리자가_해제할_때까지_차단을_유지한다() throws Exception {
        Long reportId = createReport("BLOCKAGE", photoKey, "막혔어요", null, null);
        String adminToken = privilegedToken(Role.ADMIN);

        approve(reportId, adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.blocksAdoption").value(true));

        // 승인 상태에서는 입양 차단이 유지된다
        assertThat(jdbcTemplate.queryForObject(
                "SELECT blocks_adoption FROM report WHERE id = ?", Boolean.class, reportId))
                .isTrue();

        mockMvc.perform(patch("/api/v1/admin/reports/{reportId}/clear", reportId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adminComment\":\"지자체 처리 완료\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLEARED"))
                .andExpect(jsonPath("$.data.adminComment").value("지자체 처리 완료"))
                .andExpect(jsonPath("$.data.clearedAt").exists());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM report WHERE id = ?", String.class, reportId))
                .isEqualTo("CLEARED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT cleared_at FROM report WHERE id = ?", java.sql.Timestamp.class, reportId))
                .isNotNull();
    }

    @Test
    void 승인되지_않은_제보는_입양_차단을_해제할_수_없다() throws Exception {
        Long reportId = createReport("BLOCKAGE", photoKey, "막혔어요", null, null);
        String adminToken = privilegedToken(Role.ADMIN);

        mockMvc.perform(patch("/api/v1/admin/reports/{reportId}/clear", reportId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REPORT_NOT_CLEARABLE"));
    }

    @Test
    void 악취_제보는_사진_없이_설명만으로_접수한다() throws Exception {
        Long reportId = createReport("ODOR", null, "하수구 냄새가 심해요", null, null);

        mockMvc.perform(get("/api/v1/reports/{reportId}", reportId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("ODOR"))
                .andExpect(jsonPath("$.data.description").value("하수구 냄새가 심해요"))
                .andExpect(jsonPath("$.data.photoUrl").value(nullValue()));
    }

    @Test
    void 악취_제보는_사진과_설명이_모두_없으면_400() throws Exception {
        create("ODOR", null, null, null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void 사진이_필요한_제보인데_사진이_없으면_400() throws Exception {
        create("BLOCKAGE", null, "막혔어요", null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void 위치_수정_제보는_새_위치가_없으면_400() throws Exception {
        create("LOCATION_FIX", photoKey, "위치가 달라요", null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void 철거_제보는_사유가_없으면_400() throws Exception {
        create("REMOVAL", photoKey, null, null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void 다른_사람의_제보_상세는_403이고_없는_제보는_404() throws Exception {
        Long reportId = createReport("BLOCKAGE", photoKey, "막혔어요", null, null);
        String otherToken = userToken("다른사람");

        mockMvc.perform(get("/api/v1/reports/{reportId}", reportId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/reports/{reportId}", 999_999L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("REPORT_NOT_FOUND"));
    }

    @Test
    void 관리자는_상태별_제보_목록을_조회한다() throws Exception {
        Long pendingId = createReport("BLOCKAGE", photoKey, "대기", null, null);
        Long approvedId = createReport("ODOR", null, "승인", null, null);
        jdbcTemplate.update("UPDATE report SET status = 'APPROVED' WHERE id = ?", approvedId);

        String adminToken = privilegedToken(Role.ADMIN);
        String pendingBody = mockMvc.perform(get("/api/v1/admin/reports")
                        .param("status", "PENDING")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Integer> pendingIds = JsonPath.read(pendingBody, "$.data.reports[*].reportId");
        assertThat(pendingIds).contains(pendingId.intValue())
                .doesNotContain(approvedId.intValue());

        String allBody = mockMvc.perform(get("/api/v1/admin/reports")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Integer> allIds = JsonPath.read(allBody, "$.data.reports[*].reportId");
        assertThat(allIds).contains(pendingId.intValue(), approvedId.intValue());

        mockMvc.perform(get("/api/v1/admin/reports")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void 관리자는_다른_사람의_제보_상세를_볼_수_있다() throws Exception {
        Long reportId = createReport("BLOCKAGE", photoKey, "막혔어요", null, null);
        String adminToken = privilegedToken(Role.CITY_OFFICIAL);

        mockMvc.perform(get("/api/v1/reports/{reportId}", reportId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reportId").value(reportId));
    }

    @Test
    void 관리자가_승인하면_APPROVED가_되고_경험치를_지급한다() throws Exception {
        Long reportId = createReport("BLOCKAGE", photoKey, "막혔어요", null, null);
        String adminToken = privilegedToken(Role.ADMIN);

        mockMvc.perform(patch("/api/v1/admin/reports/{reportId}", reportId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true,\"adminComment\":\"확인했어요\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.adminComment").value("확인했어요"))
                .andExpect(jsonPath("$.data.resolvedAt").exists());

        Map<String, Object> report = jdbcTemplate.queryForMap(
                "SELECT status, processed_by, admin_comment, resolved_at FROM report "
                        + "WHERE id = ?", reportId);
        assertThat(report.get("status")).isEqualTo("APPROVED");
        assertThat(report.get("processed_by")).isNotNull();
        assertThat(report.get("admin_comment")).isEqualTo("확인했어요");
        assertThat(report.get("resolved_at")).isNotNull();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT point FROM users WHERE id = ?", Integer.class, userId)).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT amount FROM point_history WHERE user_id = ? AND source_type = 'REPORT' "
                        + "AND source_id = ?", Integer.class, userId, reportId)).isEqualTo(5);
    }

    @Test
    void 반려는_사유가_필요하고_경험치를_지급하지_않는다() throws Exception {
        Long reportId = createReport("BLOCKAGE", photoKey, "막혔어요", null, null);
        String adminToken = privilegedToken(Role.ADMIN);

        mockMvc.perform(patch("/api/v1/admin/reports/{reportId}", reportId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        mockMvc.perform(patch("/api/v1/admin/reports/{reportId}", reportId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":false,\"adminComment\":\"허위 제보예요\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.adminComment").value("허위 제보예요"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT point FROM users WHERE id = ?", Integer.class, userId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM point_history WHERE user_id = ? AND source_type = 'REPORT'",
                Integer.class, userId)).isZero();
    }

    @Test
    void 이미_처리된_제보는_다시_처리할_수_없다() throws Exception {
        Long reportId = createReport("BLOCKAGE", photoKey, "막혔어요", null, null);
        String adminToken = privilegedToken(Role.ADMIN);

        approve(reportId, adminToken).andExpect(status().isOk());
        approve(reportId, adminToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REPORT_ALREADY_PROCESSED"));
    }

    @Test
    void 신규_등록_제보는_빗물받이_없이_접수한다() throws Exception {
        String response = mockMvc.perform(post("/api/v1/reports")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "NEW_DRAIN",
                                  "photoKey": "%s",
                                  "latitude": %s,
                                  "longitude": %s
                                }
                                """.formatted(photoKey, LAT, LNG)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long reportId = ((Number) JsonPath.read(response, "$.data.reportId")).longValue();

        mockMvc.perform(get("/api/v1/reports/{reportId}", reportId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("NEW_DRAIN"))
                .andExpect(jsonPath("$.data.drainId").doesNotExist())
                .andExpect(jsonPath("$.data.latitude").value(LAT));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT drain_id FROM report WHERE id = ?", Long.class, reportId)).isNull();
    }

    @Test
    void 신규_등록_제보에_빗물받이_id를_보내거나_위치가_없으면_400() throws Exception {
        mockMvc.perform(post("/api/v1/reports")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"drainId": %s, "type": "NEW_DRAIN", "photoKey": "%s",
                                 "latitude": %s, "longitude": %s}
                                """.formatted(drainId, photoKey, LAT, LNG)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        mockMvc.perform(post("/api/v1/reports")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "NEW_DRAIN", "photoKey": "%s"}
                                """.formatted(photoKey)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        mockMvc.perform(post("/api/v1/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("BLOCKAGE", photoKey, "막혔어요", null, null)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    private ResultActions create(String type, String photoKey, String description,
            Double newLatitude, Double newLongitude) throws Exception {
        return mockMvc.perform(post("/api/v1/reports")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(type, photoKey, description, newLatitude, newLongitude)));
    }

    private Long createReport(String type, String photoKey, String description,
            Double newLatitude, Double newLongitude) throws Exception {
        String response = create(type, photoKey, description, newLatitude, newLongitude)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.data.reportId")).longValue();
    }

    private ResultActions approve(Long reportId, String adminToken) throws Exception {
        return mockMvc.perform(patch("/api/v1/admin/reports/{reportId}", reportId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"approved\":true}"));
    }

    private String body(String type, String photoKey, String description,
            Double newLatitude, Double newLongitude) {
        StringBuilder body = new StringBuilder("{");
        body.append("\"drainId\":").append(drainId).append(",");
        body.append("\"type\":\"").append(type).append("\"");
        if (photoKey != null) {
            body.append(",\"photoKey\":\"").append(photoKey).append("\"");
        }
        if (description != null) {
            body.append(",\"description\":\"").append(description).append("\"");
        }
        body.append(",\"latitude\":").append(LAT).append(",\"longitude\":").append(LNG);
        if (newLatitude != null) {
            body.append(",\"newLatitude\":").append(newLatitude)
                    .append(",\"newLongitude\":").append(newLongitude);
        }
        body.append("}");
        return body.toString();
    }

    private String userToken(String nickname) {
        User user = userRepository.save(User.local(
                UUID.randomUUID() + "@test.com", "{noop}x", nickname));
        return jwtTokenProvider.createAccessToken(user.getId(), user.getRole());
    }

    private String privilegedToken(Role role) {
        User user = userRepository.save(User.local(
                UUID.randomUUID() + "@test.com", "{noop}x", role.name()));
        jdbcTemplate.update("UPDATE users SET role = ? WHERE id = ?", role.name(), user.getId());
        return jwtTokenProvider.createAccessToken(user.getId(), role);
    }

    private Long drain() {
        return jdbcTemplate.queryForObject("INSERT INTO drain (registered_by, latitude, "
                + "longitude, photo_url, dirt_level, adoption_status, status, is_risk, "
                + "created_at, updated_at) VALUES (?, ?, ?, 'http://old', 3, 'AVAILABLE', "
                + "'ACTIVE', false, now(), now()) RETURNING id", Long.class,
                userId, LAT, LNG);
    }
}
