package com.rainbutler.domain.inspection.entity;

/**
 * 점검 기록 상태입니다.
 *
 * <ul>
 *   <li>{@code NEEDS_CLEANING}: 점검 전 사진은 냈지만 청소 후 사진 인증이 남음</li>
 *   <li>{@code COMPLETED}: 점검(또는 청소) 인증을 통과함</li>
 *   <li>{@code PENDING}: 위치 불일치 · 낮은 AI 확신도로 관리자 검토 대기</li>
 *   <li>{@code REJECTED}: 관리자 검토에서 반려됨</li>
 * </ul>
 */
public enum InspectionStatus {
    NEEDS_CLEANING,
    COMPLETED,
    PENDING,
    REJECTED
}
