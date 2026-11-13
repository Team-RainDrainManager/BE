package com.rainbutler.domain.inspection.dto;

import com.rainbutler.domain.inspection.entity.InspectionStatus;

/**
 * 점검 · 청소 기록 제출 결과입니다.
 *
 * @param inspectionId 점검 기록 id
 * @param status 점검 상태 (NEEDS_CLEANING · COMPLETED · PENDING · REJECTED)
 * @param beforeGrade AI가 판정한 점검 전 등급 (1 괜찮음 · 2 쌓임 · 3 막힘)
 * @param afterGrade AI가 판정한 청소 후 등급 (1 괜찮음 · 2 쌓임 · 3 막힘, 청소 전이면 null)
 * @param confidence 점검 전 사진 AI 확신도
 * @param distanceM 촬영 위치와 빗물받이 사이 거리 (m)
 * @param xpAwarded 이번 제출로 지급한 경험치 (검토 대기 · 청소 필요면 0)
 * @param cleanNotice 사용자에게 보여줄 안내 문구
 */
public record InspectionSubmitResponse(
        Long inspectionId,
        InspectionStatus status,
        Integer beforeGrade,
        Integer afterGrade,
        Double confidence,
        Integer distanceM,
        Integer xpAwarded,
        String cleanNotice
) {
}
