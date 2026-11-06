package com.rainbutler.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.global.security.JwtTokenProvider;
import com.rainbutler.support.IntegrationTestSupport;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 실명 등록과 활동 확인서 PDF 다운로드를 검증합니다.
 */
@AutoConfigureMockMvc
class CertificateApiTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User user;
    private String token;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.local(
                UUID.randomUUID() + "@test.com", "{noop}x", "확인서집사"));
        token = jwtTokenProvider.createAccessToken(user.getId(), user.getRole());
    }

    @Test
    void 실명을_등록하면_내_정보에_인증_여부가_반영된다() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/identity")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"realName\":\"홍길동\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.realName").value("홍길동"))
                .andExpect(jsonPath("$.data.method").value("MOCK"))
                .andExpect(jsonPath("$.data.verifiedAt").exists());

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.identityVerified").value(true))
                .andExpect(jsonPath("$.data.realName").doesNotExist());
    }

    @Test
    void 실명이_비면_400() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/identity")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"realName\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void 실명을_등록하지_않으면_확인서는_409() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/certificate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("IDENTITY_VERIFICATION_REQUIRED"));
    }

    @Test
    void 활동_기록이_없으면_확인서는_409() throws Exception {
        identity("홍길동");

        mockMvc.perform(get("/api/v1/users/me/certificate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CERTIFICATE_NO_ACTIVITY"));
    }

    @Test
    void 확인서를_PDF로_다운로드한다() throws Exception {
        identity("홍길동");
        substituteCleaning();
        point(100);

        MvcResult result = mockMvc.perform(get("/api/v1/users/me/certificate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string("Content-Disposition",
                        startsWith("attachment; filename=\"activity-certificate-")))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        assertThat(body.length).isGreaterThan(3_000);
        assertThat(new String(body, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }

    private void identity(String realName) throws Exception {
        mockMvc.perform(post("/api/v1/users/me/identity")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"realName\":\"" + realName + "\"}"))
                .andExpect(status().isOk());
    }

    private void substituteCleaning() {
        Long drainId = jdbcTemplate.queryForObject("INSERT INTO drain (registered_by, latitude, "
                + "longitude, photo_url, dirt_level, adoption_status, status, is_risk, "
                + "created_at, updated_at) VALUES (?, 37.5, 127.0, 'http://x', 1, 'AVAILABLE', "
                + "'ACTIVE', false, now(), now()) RETURNING id", Long.class, user.getId());
        jdbcTemplate.update("INSERT INTO cleaning (user_id, drain_id, type, before_photo_url, "
                + "status, review_status, latitude, longitude, location_mismatch, started_at, "
                + "created_at, updated_at) VALUES (?, ?, 'SUBSTITUTE', 'http://x', 'COMPLETED', "
                + "'APPROVED', 37.5, 127.0, false, now(), now(), now())",
                user.getId(), drainId);
    }

    private void point(int amount) {
        jdbcTemplate.update("INSERT INTO point_history (user_id, source_type, source_id, amount, "
                + "type, created_at, updated_at) VALUES (?, 'INSPECTION', 1, ?, 'EARN', now(), "
                + "now())", user.getId(), amount);
    }
}
