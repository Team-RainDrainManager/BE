package com.rainbutler.domain.cleaning.dto.Response;

/**
 * 입양 청소 시작 응답입니다.
 *
 * @param cleaningId 생성된 청소 id
 * @param grade AI 판정 촬영기 등급 (0 괜찮음 · 2 쌓임 · 3 막힘)
 * @param dirtLevel AI 판정 더러움 단계 (1 괜찮음 · 2 쌓임 · 3 막힘)
 * @param needsReport 덮개 안쪽 막힘 신고 필요 여부
 * @param mustClean 청소 필요 여부 (괜찮음(0)이면 false, 쌓임(2)·막힘(3)이면 true)
 * @param blocked 입양 불가 여부 (막힘(3) 또는 needsReport=true)
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
