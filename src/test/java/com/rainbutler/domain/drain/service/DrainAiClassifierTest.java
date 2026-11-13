package com.rainbutler.domain.drain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.rainbutler.domain.drain.ai.AiProperties;
import com.rainbutler.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 촬영기 등급 → 우리 스키마 매핑 검증입니다. (네트워크 호출 없음)
 */
class DrainAiClassifierTest {

    @ParameterizedTest(name = "촬영기 {0}등급 → 더러움 {1}단계")
    @CsvSource({"0, 1", "1, 1", "2, 2", "3, 3"})
    void gradeToDirtLevel(int grade, int expected) {
        assertThat(DrainAiClassifier.toDirtLevel(grade)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "더러움 {0}단계 → 촬영기 {1}등급")
    @CsvSource({"1, 1", "2, 2", "3, 3"})
    void dirtLevelToGrade(int dirtLevel, int expected) {
        assertThat(DrainAiClassifier.gradeOfDirtLevel(dirtLevel)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "AI 답 {0} → 촬영기 {1}등급")
    @CsvSource({"괜찮음, 1", "쌓임, 2", "막힘, 3"})
    void gradeNameToValue(String name, int expected) {
        assertThat(DrainAiClassifier.gradeFromAnswer(name, null, null)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "이름 없이 덮개 {0}% · 주변 {1}% → 촬영기 {2}등급")
    @CsvSource({"20, 5, 1", "5, 20, 1", "21, 0, 2", "65, 0, 2", "0, 66, 3"})
    void gradeFromPercentWhenNameIsUnknown(int grate, int around, int expected) {
        assertThat(DrainAiClassifier.gradeFromAnswer("알 수 없음", grate, around)).isEqualTo(expected);
    }

    @Test
    @DisplayName("등급 이름도 비율도 없으면 깨끗하다고 넘기지 않고 오류로 처리한다")
    void unknownAnswerFails() {
        assertThatThrownBy(() -> DrainAiClassifier.gradeFromAnswer("알 수 없음", null, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("막힘일 때만 위험")
    void toRisk() {
        assertThat(DrainAiClassifier.toRisk(1)).isFalse();
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
                + "\"{\\\"is_drain\\\":true,\\\"grade\\\":\\\"쌓임\\\",\\\"confidence\\\":0.9,"
                + "\\\"reason\\\":\\\"낙엽이 쌓임\\\",\\\"unusable\\\":false,"
                + "\\\"needs_report\\\":false}\""
                + "}]}}]}";
        server.expect(requestTo(containsString(":generateContent")))
                .andExpect(content().string(containsString("\"temperature\":0")))
                .andExpect(content().string(containsString("괜찮음")))
                .andExpect(content().string(containsString("빨간 박스")))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        DrainAiClassifier.Assessment assessment = classifier.assess("https://example.com/photo.jpg");
        assertThat(assessment.grade()).isEqualTo(DrainAiClassifier.GRADE_PILED);
        assertThat(assessment.dirtLevel()).isEqualTo(2);
        server.verify();
    }

    @Test
    void Gemini가_일시적으로_503을_돌려주면_다시_시도한다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DrainAiClassifier classifier = new DrainAiClassifier(
                builder.build(), new AiProperties("test-key", "gemini-test")) {
            @Override
            void sleepBeforeRetry() {
                // 테스트에서는 기다리지 않는다
            }
        };

        server.expect(requestTo("https://example.com/photo.jpg"))
                .andRespond(withSuccess(new byte[] {1, 2, 3}, MediaType.IMAGE_JPEG));
        server.expect(requestTo(containsString(":generateContent")))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        String response = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":"
                + "\"{\\\"is_drain\\\":true,\\\"grade\\\":\\\"괜찮음\\\",\\\"confidence\\\":0.9,"
                + "\\\"reason\\\":\\\"깨끗함\\\",\\\"unusable\\\":false,"
                + "\\\"needs_report\\\":false}\""
                + "}]}}]}";
        server.expect(requestTo(containsString(":generateContent")))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        DrainAiClassifier.Assessment assessment = classifier.assess("https://example.com/photo.jpg");

        assertThat(assessment.grade()).isEqualTo(DrainAiClassifier.GRADE_OK);
        server.verify();
    }
}
