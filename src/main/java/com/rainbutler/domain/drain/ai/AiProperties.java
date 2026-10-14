package com.rainbutler.domain.drain.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 빗물받이 AI 판정 설정입니다. ({@code rain-butler.ai})
 *
 * @param geminiApiKey Gemini API 키 (없으면 AI 판정 없이 기본값으로 저장)
 * @param model Gemini 모델명
 */
@ConfigurationProperties(prefix = "rain-butler.ai")
public record AiProperties(
        String geminiApiKey,
        String model) {

    /**
     * 키가 설정됐는지 확인합니다. 환경변수가 없으면 "${...}" 문자열이 들어올 수 있어 함께 막습니다.
     *
     * @return 설정 여부
     */
    public boolean isConfigured() {
        return geminiApiKey != null && !geminiApiKey.isBlank() && !geminiApiKey.startsWith("${");
    }
}
