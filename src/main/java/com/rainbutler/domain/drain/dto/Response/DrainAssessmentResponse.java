package com.rainbutler.domain.drain.dto.Response;

/**
 * 빗물받이 사진 단독 판독 응답입니다. 생성·정기 점검·청소 확인이 같은 값을 씁니다.
 *
 * @param grade 촬영기 등급 (0 괜찮음 · 2 쌓임 · 3 막힘)
 * @param dirtLevel 더러움 단계 (1 괜찮음 · 2 쌓임 · 3 막힘)
 * @param isRisk 위험 빗물받이 여부 (막힘(3)일 때만 true)
 * @param needsReport 덮개 안쪽 막힘 신고 필요 여부
 * @param confidence AI 확신도 (0~1)
 * @param reason 판정 이유 한 문장
 */
public record DrainAssessmentResponse(
        Integer grade,
        Integer dirtLevel,
        boolean isRisk,
        boolean needsReport,
        Double confidence,
        String reason
) {
}
