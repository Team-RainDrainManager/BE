package com.rainbutler.global.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rainbutler.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Prometheus 지표 노출을 검증합니다. (서버 안 Grafana Alloy가 인증 없이 수집)
 */
@AutoConfigureMockMvc
class PrometheusMetricsTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 인증_없이_지표를_내려주고_공통_태그와_요청_지표가_있다() throws Exception {
        // 요청 지표가 생기도록 공개 API를 한 번 부른다
        mockMvc.perform(get("/api/v1/terms")).andExpect(status().isOk());

        String body = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .contains("http_server_requests_seconds_count")
                .contains("http_server_requests_seconds_bucket")
                .contains("application=\"rain-butler\"")
                .contains("env=\"local\"")
                .contains("jvm_memory_used_bytes")
                .contains("hikaricp_connections");
    }

    @Test
    void uri_태그는_실제_id가_아니라_템플릿이다() throws Exception {
        mockMvc.perform(get("/api/v1/drain/987654321"));

        String body = mockMvc.perform(get("/actuator/prometheus"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("uri=\"/api/v1/drain/{drainId}\"")
                .doesNotContain("uri=\"/api/v1/drain/987654321\"");
    }
}
