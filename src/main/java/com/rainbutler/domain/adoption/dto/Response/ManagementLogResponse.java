package com.rainbutler.domain.adoption.dto.Response;

import com.rainbutler.domain.adoption.entity.ManagementActionType;
import com.rainbutler.domain.adoption.entity.ManagementStatus;
import com.rainbutler.domain.adoption.entity.ManagementType;
import com.rainbutler.global.entity.ReviewStatus;
import java.time.LocalDateTime;

/**
 * 관리 기록 응답입니다.
 *
 * @param managementLogId 관리 기록 id
 * @param adoptionId 입양 id
 * @param type 관리 종류 (REGULAR · RAIN)
 * @param actionType 관리 행동 (INSPECTION · CLEANING)
 * @param status 인증 상태 (VERIFIED · FAILED)
 * @param reviewStatus 검토 상태 (위치 불일치 · 낮은 확신도면 PENDING)
 * @param locationMismatch 촬영 위치가 빗물받이와 기준 거리 이상 떨어졌는지
 * @param beforeDirtLevel 점검 전 사진 AI 더러움 단계 (1~3)
 * @param afterDirtLevel 청소 후 사진 AI 더러움 단계 (청소했을 때만)
 * @param managedAt 관리 인증 시각 (실패하면 null)
 * @param nextManagementAt 다음 관리 예정일 (실패하면 기존 값 유지)
 */
public record ManagementLogResponse(
        Long managementLogId,
        Long adoptionId,
        ManagementType type,
        ManagementActionType actionType,
        ManagementStatus status,
        ReviewStatus reviewStatus,
        boolean locationMismatch,
        int beforeDirtLevel,
        Integer afterDirtLevel,
        LocalDateTime managedAt,
        LocalDateTime nextManagementAt
) {
}
