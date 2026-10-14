package com.rainbutler.domain.drain.service;

import org.springframework.stereotype.Component;

/**
 * 빗물받이 등록 사진 AI 판정기입니다.
 *
 * <p>사진 1장으로 더러움 단계(1~3)와 위험 여부(isRisk)를 함께 판정합니다.
 * Gemini 연동 전까지 동작하는 임시 판정기입니다. Gemini 붙일 때 이 빈의 구현만 교체하면
 * {@code DrainService}는 그대로 둡니다.
 */
@Component
public class DrainAiClassifier {

    /**
     * AI 판정 결과입니다.
     *
     * @param dirtLevel 더러움 단계 (1~3)
     * @param risk 위험 빗물받이 여부
     */
    public record Assessment(int dirtLevel, boolean risk) {
    }

    /**
     * 사진 URL로 더러움 단계와 위험 여부를 판정합니다.
     *
     * @param photoUrl 현장 사진 URL
     * @return AI 판정 결과
     */
    public Assessment assess(String photoUrl) {
        // TODO: Gemini API 연동 시 실제 AI 판정으로 교체 (확신도 0.7 미만은 검토 대기)
        return new Assessment(1, false);
    }
}
