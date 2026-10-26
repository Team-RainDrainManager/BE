package com.rainbutler.domain.cleaning.dto.Response;

/**
 * 입양 청소 시작 응답입니다.
 *
 * @param cleaningId 생성된 청소 id
 * @param grade AI 판정 촬영기 등급 (0~3)
 * @param dirtLevel AI 판정 더러움 단계 (1~3)
 * @param needsReport 덮개 안쪽 막힘 신고 필요 여부
 * @param mustClean 청소 필요 여부 (1~2등급)
 * @param blocked 입양 불가 여부 (3등급·needsReport=true)
 */
public record CleaningStartResponse(
        Long cleaningId,
        int grade,
        int dirtLevel,
        boolean needsReport,
        boolean mustClean,
        boolean blocked
) {
}
