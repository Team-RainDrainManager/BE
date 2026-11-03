package com.rainbutler.domain.drain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.rainbutler.domain.drain.ai.AiProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 촬영기 등급 → 우리 스키마 매핑 검증입니다. (네트워크 호출 없음)
 */
class DrainAiClassifierTest {

    @ParameterizedTest(name = "촬영기 {0}등급 → 더러움 {1}단계")
    @CsvSource({"0, 1", "1, 2", "2, 3", "3, 3"})
    void gradeToDirtLevel(int grade, int expected) {
        assertThat(DrainAiClassifier.toDirtLevel(grade)).isEqualTo(expected);
    }

    @Test
    @DisplayName("3등급일 때만 위험")
    void toRisk() {
        assertThat(DrainAiClassifier.toRisk(0)).isFalse();
        assertThat(DrainAiClassifier.toRisk(2)).isFalse();
        assertThat(DrainAiClassifier.toRisk(3)).isTrue();
    }

    @Test
    void 판정_요청은_온도를_0으로_고정한다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DrainAiClassifier classifier = new DrainAiClassifier(
                builder.build(), new AiProperties("test-key", "gemini-test"));

        server.expect(requestTo("https://example.com/photo.jpg"))
                .andRespond(withSuccess(new byte[] {1, 2, 3}, MediaType.IMAGE_JPEG));
        String response = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":"
                + "\"{\\\"is_drain\\\":true,\\\"grade\\\":0,\\\"confidence\\\":0.9,"
                + "\\\"reason\\\":\\\"깨끗함\\\",\\\"unusable\\\":false,"
                + "\\\"needs_report\\\":false}\""
                + "}]}}]}";
        server.expect(requestTo(containsString(":generateContent")))
                .andExpect(content().string(containsString("\"temperature\":0")))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        assertThat(classifier.assess("https://example.com/photo.jpg").dirtLevel()).isEqualTo(1);
        server.verify();
    }
}
