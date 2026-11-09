package com.rainbutler.domain.drain.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rainbutler.domain.drain.ai.AiProperties;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import java.net.URI;
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
 * 판정 기준과 JSON 스키마는 현장 조사용 촬영기 앱의 등급 기준 v6(3단계)과 같습니다.
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

    /** 괜찮음. 치울 필요 없음. 덮개와 주변 둘 다 20% 이하 */
    public static final int GRADE_OK = 0;
    /** 쌓임. 치워야 함. 더 심한 쪽이 20% 넘고 66% 미만 */
    public static final int GRADE_PILED = 2;
    /** 막힘. 더 심한 쪽이 66% 이상. 값 1은 옛 기준 '조금 쌓임'으로 예전 기록에만 남아 있다 */
    public static final int GRADE_BLOCKED = 3;

    private static final int OK_MAX_PERCENT = 20;
    private static final int BLOCKED_MIN_PERCENT = 66;
    private static final String NAME_OK = "괜찮음";
    private static final String NAME_PILED = "쌓임";
    private static final String NAME_BLOCKED = "막힘";

    private final RestClient restClient;
    private final AiProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DrainAiClassifier(
            @Qualifier("geminiRestClient") RestClient restClient,
            AiProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
        if (!properties.isConfigured()) {
            log.warn("Gemini 키(GEMINI_API_KEY)가 설정되지 않아 AI 판정 없이 기본값으로 저장합니다.");
        }
    }

    /**
     * AI 판정 결과입니다. 생성·정기 점검·청소 확인이 공통으로 씁니다.
     *
     * @param dirtLevel 더러움 단계 (1 괜찮음 · 2 쌓임 · 3 막힘)
     * @param risk 위험 빗물받이 여부 (막힘일 때만 true, 지자체 신고 대상)
     * @param confidence AI 확신도 (0~1)
     * @param reason 판정 이유 한 문장
     * @param grade 촬영기 등급 (0 괜찮음 · 2 쌓임 · 3 막힘)
     * @param needsReport 덮개 안쪽 막힘 신고 필요 여부
     */
    public record Assessment(
            int dirtLevel,
            boolean risk,
            double confidence,
            String reason,
            int grade,
            boolean needsReport) {
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
            return new Assessment(1, false, 1.0, "AI 미설정 기본값", 0, false);
        }
        byte[] image = downloadPhoto(photoUrl);
        GeminiGrade grade = gradePhoto(image);
        if (!grade.isDrain() || grade.unusable()) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "빗물받이 사진이 아니에요. 빗물받이 덮개가 보이게 다시 찍어 주세요.");
        }
        int gradeValue = gradeFromAnswer(grade.grade(), grade.gratePercent(), grade.aroundPercent());
        return new Assessment(
                toDirtLevel(gradeValue),
                toRisk(gradeValue),
                grade.confidence(),
                grade.reason(),
                gradeValue,
                grade.needsReport());
    }

    /**
     * AI가 답한 등급 이름을 저장 값으로 바꿉니다. 이름을 읽지 못하면 덮개 · 주변 비율 중 큰 값으로 정합니다.
     *
     * @param name AI가 답한 등급 이름
     * @param gratePercent 덮개를 가린 비율 (없으면 null)
     * @param aroundPercent 주변을 덮은 비율 (없으면 null)
     * @return 촬영기 등급 (0 괜찮음 · 2 쌓임 · 3 막힘)
     * @throws BusinessException 이름도 비율도 읽을 수 없으면
     */
    static int gradeFromAnswer(String name, Integer gratePercent, Integer aroundPercent) {
        if (name != null) {
            if (name.contains(NAME_OK)) {
                return GRADE_OK;
            }
            if (name.contains(NAME_PILED)) {
                return GRADE_PILED;
            }
            if (name.contains(NAME_BLOCKED)) {
                return GRADE_BLOCKED;
            }
        }
        if (gratePercent == null && aroundPercent == null) {
            log.error("[AI 등급 해석 실패] {}", name);
            throw new BusinessException(ErrorCode.AI_API_ERROR);
        }
        int percent = Math.max(
                gratePercent == null ? 0 : gratePercent,
                aroundPercent == null ? 0 : aroundPercent);
        if (percent <= OK_MAX_PERCENT) {
            return GRADE_OK;
        }
        return percent < BLOCKED_MIN_PERCENT ? GRADE_PILED : GRADE_BLOCKED;
    }

    /**
     * 촬영기 등급을 우리 더러움 단계(1~3)로 바꿉니다.
     *
     * <p>괜찮음은 1단계, 쌓임은 2단계, 막힘은 3단계입니다. 옛 기준 1등급(조금 쌓임)은 2단계로 봅니다.
     *
     * @param grade 촬영기 등급 (0 괜찮음 · 2 쌓임 · 3 막힘)
     * @return 더러움 단계 (1~3)
     */
    public static int toDirtLevel(int grade) {
        if (grade <= GRADE_OK) {
            return 1;
        }
        return grade >= GRADE_BLOCKED ? 3 : 2;
    }

    /**
     * 더러움 단계를 촬영기 등급으로 되돌립니다. 입양 청소는 더러움 단계만 저장하므로 청소 인증 때 씁니다.
     *
     * @param dirtLevel 더러움 단계 (1~3)
     * @return 촬영기 등급 (0 괜찮음 · 2 쌓임 · 3 막힘)
     */
    public static int gradeOfDirtLevel(int dirtLevel) {
        if (dirtLevel <= 1) {
            return GRADE_OK;
        }
        return dirtLevel >= 3 ? GRADE_BLOCKED : GRADE_PILED;
    }

    /**
     * 위험 여부를 정합니다. (막힘일 때만 위험 → 지자체 신고 대상)
     *
     * @param grade 촬영기 등급 (0 괜찮음 · 2 쌓임 · 3 막힘)
     * @return 위험 여부
     */
    static boolean toRisk(int grade) {
        return grade >= GRADE_BLOCKED;
    }

    /**
     * 사진 URL에서 이미지 바이트를 내려받습니다.
     */
    private byte[] downloadPhoto(String photoUrl) {
        try {
            // presigned URL은 이미 인코딩돼 있어 문자열로 넘기면 다시 인코딩돼 서명이 깨진다 → URI로 넘긴다
            byte[] body = restClient.get()
                    .uri(URI.create(photoUrl))
                    .retrieve()
                    .body(byte[].class);
            if (body == null || body.length == 0) {
                throw new BusinessException(
                        ErrorCode.INVALID_INPUT,
                        "사진을 가져오지 못했어요. 사진 주소를 확인해 주세요.");
            }
            return body;
        } catch (RestClientException | IllegalArgumentException e) {
            // IllegalArgumentException: URI.create가 형식이 잘못된 주소를 거절한 경우
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
                        // 같은 사진은 같은 판정이 나오도록 온도를 0으로 고정한다
                        "temperature", 0,
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
     * 등급 기준 v6 (촬영기 앱과 같은 문장, 빨간 박스 대신 사진 전체에서 덮개를 찾습니다).
     */
    private String prompt() {
        return String.join("\n",
                "너는 빗물받이 덮개와 그 주변에 쓰레기가 얼마나 쌓였는지 판정한다.",
                "사진에서 빗물받이 덮개를 찾고, 덮개와 그 바로 옆을 따로 본 뒤 둘 중 더 심한 쪽으로 등급을 매긴다. 판정 범위는 덮개와 그 바로 옆까지이고, 그 밖의 길바닥·인도·차도는 사진에 보여도 판정에 넣지 않는다.",
                "등급 기준:",
                "- 괜찮음: 치울 필요 없다. 덮개와 주변 둘 다 20% 이하 (꽁초·낙엽 몇 개는 괜찮음)",
                "- 쌓임: 치워야 한다. 더 심한 쪽이 20% 넘고 3분의 2(66%) 미만 덮였다",
                "- 막힘: 더 심한 쪽이 3분의 2(66%) 이상 덮였다. 덮개가 다 막힌 것도 여기",
                "- 판단 불가: 빗물받이가 아니거나, 흐리거나, 너무 멀어서 쓰레기 양을 알 수 없다. 이때 unusable=true 로 답한다.",
                "- 신고 필요(등급과 별개): 빗물받이 아래(덮개 틈 안쪽)에 쓰레기·흙·낙엽이 쌓여 있다. 시민이 치울 수 없어 지자체에 신고한다. 이때 needs_report=true 로 답한다.",
                "규칙:",
                "- 물길을 막는 것만 센다: 쓰레기·낙엽·담배꽁초·흙더미, 덮개 위를 덮어 둔 고무판·장판·비닐. 얇게 깔린 흙먼지·모래, 얼룩, 이끼는 세지 않는다.",
                "- 빗물받이 아래(덮개 틈으로 보이는 안쪽)에 쌓인 쓰레기는 등급에 넣지 않고 신고 필요로 따로 표시한다.",
                "- 사진에 보이는 것만 보고 매긴다.",
                "- 애매하면 괜찮음과 쌓임 사이는 괜찮음, 쌓임과 막힘 사이는 막힘을 고른다.",
                "grate_percent 는 덮개 면적 중 물길을 막는 것이 위에서 가린 비율이다.",
                "around_percent 는 덮개 테두리에서 바깥으로 덮개 폭의 5분의 1 너비만큼 둘러싼 좁은 띠 안에서 물길을 막는 것이 덮은 비율이다. 그 띠 밖에 있는 낙엽과 쓰레기는 세지 않는다.",
                "grade 는 grate_percent 와 around_percent 중 큰 값으로 정한다. 20 이하면 괜찮음, 66 이상이면 막힘, 그 사이는 쌓임. 경계에서 애매할 때는 위 규칙을 따른다.",
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
        properties.put("grade", Map.of(
                "type", "STRING",
                "enum", List.of(NAME_OK, NAME_PILED, NAME_BLOCKED)));
        properties.put("needs_report", Map.of("type", "BOOLEAN"));
        properties.put("confidence", Map.of("type", "NUMBER"));
        properties.put("causes", Map.of(
                "type", "ARRAY",
                "items", Map.of("type", "STRING")));
        properties.put("reason", Map.of("type", "STRING"));
        return Map.of(
                "type", "OBJECT",
                "properties", properties,
                "required", List.of("is_drain", "unusable", "grate_percent", "around_percent",
                        "grade", "needs_report", "confidence", "reason"),
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
            @JsonProperty("grate_percent") Integer gratePercent,
            @JsonProperty("around_percent") Integer aroundPercent,
            @JsonProperty("grade") String grade,
            @JsonProperty("needs_report") boolean needsReport,
            @JsonProperty("confidence") double confidence,
            @JsonProperty("reason") String reason) {
    }
}
