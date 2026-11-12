package com.rainbutler.domain.inspection.dto;

import com.rainbutler.domain.drain.entity.DrainDisplayStatus;
import com.rainbutler.domain.inspection.entity.InspectionKind;
import com.rainbutler.global.entity.ReviewStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 점검 기록 상세 응답입니다.
 *
 * <p>사진은 저장 key를 조회 URL로 바꿔 내려줍니다. 촬영일로부터 사진 보존 기간이 지나면
 * {@code photoDeleted}가 true이고 사진 URL은 null입니다.
 *
 * @param inspectionId 점검 기록 id
 * @param drain 점검한 빗물받이 (id · 표시 이름)
 * @param kind 점검 종류 (ROUTINE · FORECAST · SUBSTITUTE)
 * @param createdAt 기록 생성 시각
 * @param review 검토 상태 (NOT_REQUIRED · PENDING · APPROVED · REJECTED)
 * @param reviewReason 검토가 필요하거나 반려된 이유 (없으면 null)
 * @param beforePhotoUrl 점검 전 사진 조회 URL (사진이 삭제됐으면 null)
 * @param afterPhotoUrl 청소 후 사진 조회 URL (청소 전 · 사진이 삭제됐으면 null)
 * @param beforeGrade AI가 판정한 점검 전 등급 (0 괜찮음 · 2 쌓임 · 3 막힘)
 * @param afterGrade AI가 판정한 청소 후 등급 (0 괜찮음 · 2 쌓임 · 3 막힘, 청소 전이면 null)
 * @param cleaned 청소 후 사진을 낸 기록인지
 * @param distanceM 촬영 위치와 빗물받이 사이 거리 (m)
 * @param xp 이 점검으로 지급한 경험치
 * @param note 점검 메모 (없으면 null)
 * @param photoDeleted 사진 보존 기간이 지나 사진을 내려주지 않는지
 * @param level 입양 레벨 (입양 없으면 0)
 * @param displayStatus 지도 표시 상태 (미입양·정상·점검 지연·막힘 제보)
 * @param hasBlockageReport 막힘 제보가 있는지
 */
public record InspectionDetailResponse(
        Long inspectionId,
        DrainResponse drain,
        InspectionKind kind,
        LocalDateTime createdAt,
        ReviewStatus review,
        String reviewReason,
        String beforePhotoUrl,
        String afterPhotoUrl,
        Integer beforeGrade,
        Integer afterGrade,
        boolean cleaned,
        Integer distanceM,
        Integer xp,
        String note,
        boolean photoDeleted,
        Integer level,
        DrainDisplayStatus displayStatus,
        boolean hasBlockageReport
) {

    /**
     * 점검 대상 빗물받이입니다.
     *
     * <p>{@code DrainResponse}라는 이름이 빗물받이 목록 DTO와 겹쳐 Swagger 컴포넌트가 덮어써지지
     * 않도록 문서 스키마 이름을 따로 둡니다.
     *
     * @param drainId 빗물받이 id
     * @param name 화면에 보여줄 이름 (입양 당시 별명, 없으면 빗물받이 id 표기)
     */
    @Schema(name = "InspectionDrainResponse", description = "점검 대상 빗물받이 요약")
    public record DrainResponse(Long drainId, String name) {
    }
}
