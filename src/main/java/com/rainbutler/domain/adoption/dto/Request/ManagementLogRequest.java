package com.rainbutler.domain.adoption.dto.Request;

import com.rainbutler.domain.adoption.entity.ManagementActionType;
import com.rainbutler.domain.adoption.entity.ManagementType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 관리(점검 · 청소) 기록 요청입니다.
 *
 * <p>점검 전 사진(BEFORE)과 촬영 위치가 필수입니다. 청소까지 했다면({@code actionType=CLEANING}) 청소 후
 * 사진(AFTER)도 필요합니다. 사진은 {@code POST /api/v1/uploads/presigned-url}로 S3에 먼저 올리고 받은
 * {@code key}를 보냅니다.
 *
 * <p>비 예보 점검({@code type=RAIN})은 응답한 점검 요청의 {@code requestId}를 함께 보내야 합니다. 서버가
 * AI로 더러움 단계를 판정하고, 촬영 위치가 빗물받이에서 너무 멀거나 AI 확신도가 낮으면 관리자 검토 대기로
 * 남깁니다.
 *
 * @param type 관리 종류 (REGULAR · RAIN)
 * @param requestId 비 예보 점검 요청 id (type=RAIN일 때 필수)
 * @param actionType 관리 행동 (INSPECTION · CLEANING)
 * @param beforePhotoKey 점검 전 사진 key
 * @param afterPhotoKey 청소 후 사진 key (actionType=CLEANING일 때 필수)
 * @param memo 남길 메모
 * @param latitude 촬영 위도
 * @param longitude 촬영 경도
 * @param safetyAgreed 안전 수칙 동의 여부 (필수)
 */
public record ManagementLogRequest(
        @NotNull(message = "관리 종류를 선택해 주세요.")
        ManagementType type,

        @Positive(message = "점검 요청 id가 올바르지 않아요.")
        Long requestId,

        @NotNull(message = "관리 행동을 선택해 주세요.")
        ManagementActionType actionType,

        @NotBlank(message = "점검 전 사진을 등록해 주세요.")
        String beforePhotoKey,

        String afterPhotoKey,

        @Size(max = 500, message = "메모는 500자 이하로 남겨 주세요.")
        String memo,

        @NotNull(message = "촬영 위도를 입력해 주세요.")
        @DecimalMin(value = "-90.0", message = "위도는 -90 이상 90 이하여야 합니다.")
        @DecimalMax(value = "90.0", message = "위도는 -90 이상 90 이하여야 합니다.")
        Double latitude,

        @NotNull(message = "촬영 경도를 입력해 주세요.")
        @DecimalMin(value = "-180.0", message = "경도는 -180 이상 180 이하여야 합니다.")
        @DecimalMax(value = "180.0", message = "경도는 -180 이상 180 이하여야 합니다.")
        Double longitude,

        @NotNull(message = "안전 동의가 필요합니다.")
        Boolean safetyAgreed
) {
}
