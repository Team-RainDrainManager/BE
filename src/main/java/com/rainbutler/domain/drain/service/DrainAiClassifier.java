package com.rainbutler.domain.drain.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rainbutler.domain.drain.ai.AiProperties;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 빗물받이 등록 사진 AI 판정기입니다.
 *
 * <p>사진 1장을 내려받아 Gemini에 보내 더러움 단계(1~3)와 위험 여부(isRisk)를 함께 판정합니다.
 * 판정 기준과 JSON 스키마는 현장 조사용 촬영기 앱의 등급 기준 v5와 같습니다.
 *
 * <p>다음에 만들 AI 판독 API도 이 빈을 그대로 쓰면 됩니다. 생성 흐름과 단독 판독이 같은 기준·같은
 * 매핑으로 동작합니다.
 *
 * <p>Gemini 키가 없으면 외부 호출 없이 기본값(1단계·안전)으로 돌려줘 목 데이터 테스트가 깨지지 않습니다.
 */
@Slf4j
@Component
public class DrainAiClassifier {

    private static final String GENERATE_CONTENT_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent";
    private static final int MAX_RETRIES = 2;
    private static final long RETRY_WAIT_MILLIS = 5000;

    private final RestClient restClient;
    private final AiProperties properties;
    private final ObjectMapper objectMapper;

    public DrainAiClassifier(
            @Qualifier("geminiRestClient") RestClient restClient,
            AiProperties properties,
            ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        if (!properties.isConfigured()) {
            log.warn("Gemini 키(GEMINI_API_KEY)가 설정되지 않아 AI 판정 없이 기본값으로 저장합니다.");
        }
    }

    /**
     * AI 판정 결과입니다.
     *
     * @param dirtLevel 더러움 단계 (1~3, 촬영기 등급 +1을 3으로 자른 값)
     * @param risk 위험 빗물받이 여부 (신고 필요 또는 3등급)
     * @param confidence AI 확신도 (0~1)
     * @param reason 판정 이유 한 문장
     */
    public record Assessment(int dirtLevel, boolean risk, double confidence, String reason) {
    }

    /**
     * 사진 URL로 더러움 단계와 위험 여부를 판정합니다.
     *
     * @param photoUrl 현장 사진 URL
     * @return AI 판정 결과
     */
    public Assessment assess(String photoUrl) {
        if (!properties.isConfigured()) {
            // 키 없으면 목 테스트용 기본값 (확신도 1.0이라 검토 제보가 생기지 않는다)
            return new Assessment(1, false, 1.0, "AI 미설정 기본값");
        }
        byte[] image = downloadPhoto(photoUrl);
        GeminiGrade grade = gradePhoto(image);
        if (!grade.isDrain() || grade.unusable()) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "빗물받이 사진이 아니에요. 빗물받이 덮개가 보이게 다시 찍어 주세요.");
        }
        return new Assessment(
                toDirtLevel(grade.grade()),
                toRisk(grade.grade(), grade.needsReport()),
                grade.confidence(),
                grade.reason());
    }

    /**
     * 촬영기 등급(0~3)을 우리 더러움 단계(1~3)로 바꿉니다. (0 깨끗함은 1단계에 넣습니다)
     *
     * @param grade 촬영기 등급 (0~3)
     * @return 더러움 단계 (1~3)
     */
    static int toDirtLevel(int grade) {
        return Math.min(Math.max(grade, 0) + 1, 3);
    }

    /**
     * 위험 여부를 정합니다. (덮개 안쪽 막힘 신고 필요 또는 3등급이면 위험)
     *
     * @param grade 촬영기 등급 (0~3)
     * @param needsReport 덮개 안쪽 막힘 신고 필요 여부
     * @return 위험 여부
     */
    static boolean toRisk(int grade, boolean needsReport) {
        return needsReport || grade >= 3;
    }

    /**
     * 사진 URL에서 이미지 바이트를 내려받습니다.
     */
    private byte[] downloadPhoto(String photoUrl) {
        try {
            byte[] body = restClient.get()
                    .uri(photoUrl)
                    .retrieve()
                    .body(byte[].class);
            if (body == null || body.length == 0) {
                throw new BusinessException(
                        ErrorCode.INVALID_INPUT,
                        "사진을 가져오지 못했어요. 사진 주소를 확인해 주세요.");
            }
            return body;
        } catch (RestClientException e) {
            log.warn("[AI 사진 내려받기 실패] {}", e.getMessage());
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "사진을 가져오지 못했어요. 사진 주소를 확인해 주세요.");
        }
    }

    /**
     * Gemini에 사진을 보내 등급을 매깁니다. (429면 최대 2회 재시도)
     */
    private GeminiGrade gradePhoto(byte[] image) {
        String base64 = Base64.getEncoder().encodeToString(image);
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(
                                Map.of("text", prompt()),
                                Map.of("inlineData", Map.of(
                                        "mimeType", "image/jpeg",
                                        "data", base64))))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseSchema", responseSchema()));

        for (int attempt = 0; ; attempt++) {
            try {
                GeminiResponse response = restClient.post()
                        .uri(GENERATE_CONTENT_URL, properties.model())
                        .header("x-goog-api-key", properties.geminiApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(GeminiResponse.class);
                return parseGrade(response);
            } catch (HttpClientErrorException.TooManyRequests e) {
                if (attempt >= MAX_RETRIES) {
                    throw new BusinessException(ErrorCode.AI_API_ERROR);
                }
                log.warn("[AI 한도 초과, {}초 후 재시도 {}/{}]", RETRY_WAIT_MILLIS / 1000, attempt + 1,
                        MAX_RETRIES);
                sleepBeforeRetry();
            } catch (RestClientException e) {
                log.error("[AI 판정 오류] {}", e.getMessage(), e);
                throw new BusinessException(ErrorCode.AI_API_ERROR);
            }
        }
    }

    private GeminiGrade parseGrade(GeminiResponse response) {
        String text = null;
        if (response != null && response.candidates() != null && !response.candidates().isEmpty()
                && response.candidates().get(0).content() != null
                && response.candidates().get(0).content().parts() != null) {
            text = response.candidates().get(0).content().parts().stream()
                    .filter(part -> part.text() != null)
                    .map(GeminiPart::text)
                    .reduce("", (a, b) -> a + b);
        }
        if (text == null || text.isBlank()) {
            throw new BusinessException(ErrorCode.AI_API_ERROR);
        }
        try {
            return objectMapper.readValue(text, GeminiGrade.class);
        } catch (JsonProcessingException e) {
            log.error("[AI 응답 해석 실패] {}", text);
            throw new BusinessException(ErrorCode.AI_API_ERROR);
        }
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_WAIT_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.AI_API_ERROR);
        }
    }

    /**
     * 등급 기준 v5 (촬영기 앱과 같은 문장, 빨간 박스 대신 사진 전체에서 덮개를 찾습니다).
     */
    private String prompt() {
        return String.join("\n",
                "너는 빗물받이 덮개와 그 주변에 쓰레기가 얼마나 쌓였는지 판정한다.",
                "사진에서 빗물받이 덮개를 찾고, 덮개와 그 주변을 따로 본 뒤 둘 중 더 심한 쪽으로 등급을 매긴다.",
                "등급 기준:",
                "- 0등급(깨끗함): 덮개와 주변 둘 다 쓰레기가 5% 미만으로 덮음",
                "- 1등급(조금 쌓임): 더 심한 쪽이 5~33% 덮음",
                "- 2등급(꽤 쌓임): 더 심한 쪽이 33~66% 덮음",
                "- 3등급(많이 막힘): 더 심한 쪽이 66% 이상 덮음",
                "- 판단 불가: 빗물받이가 아니거나 흐리거나 너무 멀어서 알 수 없음. 이때 unusable=true 로 답한다.",
                "신고 필요(등급과 별개): 덮개 틈 안쪽에 쓰레기가 쌓여 시민이 치울 수 없으면 needs_report=true 로 답한다.",
                "쓰레기·낙엽·흙·담배꽁초를 모두 센다. 덮개 위에 덮어 둔 고무판·장판·비닐도 쓰레기로 센다.",
                "덮개가 사진에 없으면 is_drain=false, unusable=true 로 답한다.",
                "정해진 JSON 형식으로만 답한다.");
    }

    /**
     * Gemini 구조화 출력 스키마 (촬영기 앱과 같은 항목).
     */
    private Map<String, Object> responseSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("is_drain", Map.of("type", "BOOLEAN"));
        properties.put("unusable", Map.of("type", "BOOLEAN"));
        properties.put("grate_percent", Map.of("type", "INTEGER"));
        properties.put("around_percent", Map.of("type", "INTEGER"));
        properties.put("grade", Map.of("type", "INTEGER"));
        properties.put("needs_report", Map.of("type", "BOOLEAN"));
        properties.put("confidence", Map.of("type", "NUMBER"));
        properties.put("causes", Map.of(
                "type", "ARRAY",
                "items", Map.of("type", "STRING")));
        properties.put("reason", Map.of("type", "STRING"));
        return Map.of(
                "type", "OBJECT",
                "properties", properties,
                "required", List.of("is_drain", "unusable", "grade", "needs_report",
                        "confidence", "reason"),
                "propertyOrdering", List.of("is_drain", "unusable", "grate_percent",
                        "around_percent", "grade", "needs_report", "confidence", "causes",
                        "reason"));
    }

    /** Gemini generateContent 응답 (필요한 값만) */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiResponse(List<GeminiCandidate> candidates) {
    }

    /** 후보 1개 (필요한 값만) */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiCandidate(GeminiContent content) {
    }

    /** content.parts (필요한 값만) */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiContent(List<GeminiPart> parts) {
    }

    /** 텍스트 조각 (필요한 값만) */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiPart(String text) {
    }

    /** AI 등급 결과 (필요한 값만) */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiGrade(
            @JsonProperty("is_drain") boolean isDrain,
            @JsonProperty("unusable") boolean unusable,
            @JsonProperty("grade") int grade,
            @JsonProperty("needs_report") boolean needsReport,
            @JsonProperty("confidence") double confidence,
            @JsonProperty("reason") String reason) {
    }
}
