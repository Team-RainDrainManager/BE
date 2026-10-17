package com.rainbutler.domain.terms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.rainbutler.domain.terms.entity.Terms;
import com.rainbutler.domain.terms.repository.TermsRepository;
import com.rainbutler.support.IntegrationTestSupport;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 약관 목록 · 전문 API를 검증합니다. (로그인 없이 호출)
 */
@AutoConfigureMockMvc
class TermsApiTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TermsRepository termsRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 약관_목록은_로그인_없이_순서대로_7개를_준다() throws Exception {
        mockMvc.perform(get("/api/v1/terms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(7))
                .andExpect(jsonPath("$.data[*].type").value(contains(
                        "AGE_14", "SERVICE", "PRIVACY", "LOCATION", "SAFETY", "NOTIFICATION",
                        "NIGHT_PUSH")))
                .andExpect(jsonPath("$.data[*].displayOrder")
                        .value(contains(1, 2, 3, 4, 5, 6, 7)))
                .andExpect(jsonPath("$.data[*].required").value(contains(
                        true, true, true, true, true, false, false)))
                .andExpect(jsonPath("$.data[0].hasContent").value(false))
                .andExpect(jsonPath("$.data[0].title").value("만 14세 이상입니다"));
    }

    @Test
    void 시행된_최신_버전만_보이고_시행_전_버전은_보이지_않는다() throws Exception {
        Long v2 = insertServiceTerms(2, "now() - interval '1 minute'");
        Long future = insertServiceTerms(3, "now() + interval '1 day'");
        try {
            String body = mockMvc.perform(get("/api/v1/terms"))
                    .andExpect(jsonPath("$.data.length()").value(7))
                    .andReturn().getResponse().getContentAsString();
            List<Integer> serviceIds =
                    JsonPath.read(body, "$.data[?(@.type == 'SERVICE')].termsId");
            assertThat(serviceIds).containsExactly(v2.intValue());
        } finally {
            jdbcTemplate.update("DELETE FROM terms WHERE id IN (?, ?)", v2, future);
        }
    }

    @Test
    void 약관_전문은_리소스_파일_내용을_그대로_준다() throws Exception {
        String expected = new String(new ClassPathResource("terms/SERVICE_v1.md")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        mockMvc.perform(get("/api/v1/terms/" + idOf("SERVICE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("SERVICE"))
                .andExpect(jsonPath("$.data.content").value(expected));
    }

    @Test
    void 전문이_없는_AGE_14는_content가_null이고_없는_약관은_404() throws Exception {
        mockMvc.perform(get("/api/v1/terms/" + idOf("AGE_14")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").doesNotExist());
        mockMvc.perform(get("/api/v1/terms/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TERMS_NOT_FOUND"));
    }

    @Test
    void 시드의_모든_전문_경로_파일이_classpath에_있다() {
        List<Terms> withContent = termsRepository.findAll().stream()
                .filter(Terms::hasContent)
                .toList();

        assertThat(withContent).hasSize(6);
        assertThat(withContent).allSatisfy(terms ->
                assertThat(new ClassPathResource(terms.getContentPath()).exists())
                        .as(terms.getContentPath()).isTrue());
    }

    private Long idOf(String type) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM terms WHERE type = ? AND version = 1", Long.class, type);
    }

    private Long insertServiceTerms(int version, String effectiveAtSql) {
        return jdbcTemplate.queryForObject("INSERT INTO terms (type, version, title, required, "
                + "content_path, display_order, effective_at, created_at, updated_at) VALUES "
                + "('SERVICE', ?, '서비스 이용약관에 동의합니다', true, 'terms/SERVICE_v1.md', 2, "
                + effectiveAtSql + ", now(), now()) RETURNING id", Long.class, version);
    }
}
