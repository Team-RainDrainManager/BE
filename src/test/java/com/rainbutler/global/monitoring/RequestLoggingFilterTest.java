package com.rainbutler.global.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rainbutler.domain.user.entity.Role;
import com.rainbutler.global.logging.RequestLoggingFilter;
import com.rainbutler.global.security.JwtTokenProvider;
import com.rainbutler.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 요청 ID · 접근 로그 필터를 검증합니다.
 */
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class RequestLoggingFilterTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void 요청_ID를_만들어_응답_헤더로_돌려주고_접근_로그를_남긴다(CapturedOutput output) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/terms").param("secret", "presigned-sig"))
                .andExpect(status().isOk())
                .andExpect(header().exists(RequestLoggingFilter.REQUEST_ID_HEADER))
                .andReturn();
        String requestId = result.getResponse().getHeader(RequestLoggingFilter.REQUEST_ID_HEADER);

        assertThat(requestId).hasSize(8);
        assertThat(output.getOut())
                .contains("GET /api/v1/terms 200")
                .contains("user=-")
                // 쿼리스트링(presigned URL 서명 등)은 남기지 않는다
                .doesNotContain("presigned-sig");
        // 요청이 끝나면 MDC를 비운다
        assertThat(MDC.get(RequestLoggingFilter.REQUEST_ID)).isNull();
        assertThat(MDC.get(RequestLoggingFilter.USER_ID)).isNull();
    }

    @Test
    void 들어온_요청_ID는_유지하고_이상한_값은_새로_만든다() throws Exception {
        mockMvc.perform(get("/api/v1/terms").header("X-Request-Id", "front-1234"))
                .andExpect(header().string("X-Request-Id", "front-1234"));

        String replaced = mockMvc.perform(get("/api/v1/terms")
                        .header("X-Request-Id", "bad id\nfake log line"))
                .andReturn().getResponse().getHeader("X-Request-Id");
        assertThat(replaced).hasSize(8).doesNotContain(" ");
    }

    @Test
    void 인증된_요청은_userId를_남기고_토큰은_남기지_않는다(CapturedOutput output) throws Exception {
        String token = jwtTokenProvider.createAccessToken(424242L, Role.USER);

        mockMvc.perform(get("/api/v1/users/me/settings")
                .header("Authorization", "Bearer " + token)
                .header("X-Forwarded-For", "203.0.113.7, 10.0.0.1"));

        assertThat(output.getOut())
                .contains("/api/v1/users/me/settings")
                .contains("user=424242")
                .contains("ip=203.0.113.7")
                .doesNotContain(token)
                .doesNotContain("Bearer ");
    }

    @Test
    void actuator_요청은_요청_ID도_접근_로그도_남기지_않는다(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(header().doesNotExist(RequestLoggingFilter.REQUEST_ID_HEADER));

        assertThat(output.getOut()).doesNotContain("GET /actuator/health");
    }
}
