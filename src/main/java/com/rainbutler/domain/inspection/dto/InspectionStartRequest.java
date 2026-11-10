package com.rainbutler.domain.inspection.dto;

import com.rainbutler.domain.inspection.entity.InspectionKind;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/**
 * 점검 시작 요청입니다. 점검 전 사진을 내고 AI 판정을 받습니다.
 *
 * @param drainId 점검할 빗물받이 id
 * @param kind 점검 종류 (정기 · 비 예보 · 대신 점검)
 * @param requestId 비 예보 · 대신 점검 요청 id (정기 점검이면 null)
 * @param beforeKey 점검 전 사진 key
 * @param latitude 촬영 위도
 * @param longitude 촬영 경도
 * @param capturedAt 사진 촬영 시각 (시간대 포함, 예: 2026-11-09T01:00:00Z)
 * @param note 남길 메모 (최대 500자)
 * @param safetyConfirmed 현장 안전 수칙 확인 여부 (필수)
 */
public record InspectionStartRequest(
        @NotNull Long drainId,
        @NotNull InspectionKind kind,
        Long requestId,
        @NotBlank String beforeKey,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @NotNull OffsetDateTime capturedAt,
        @Size(max = 500) String note,
        @NotNull Boolean safetyConfirmed
) {
}
