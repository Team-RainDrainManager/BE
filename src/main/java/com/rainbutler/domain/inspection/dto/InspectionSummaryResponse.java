package com.rainbutler.domain.inspection.dto;

import com.rainbutler.domain.inspection.entity.InspectionKind;
import com.rainbutler.global.entity.ReviewStatus;
import java.time.LocalDateTime;

/**
 * 입양별 점검 기록 목록의 한 건입니다.
 *
 * <p>빗물받이 상세의 "최근 관리 기록"에 쓰는 요약입니다. 사진은 저장 key를 조회 URL로 바꿔 내려주며,
 * 촬영일로부터 사진 보존 기간이 지나면 {@code photoDeleted}가 true이고 사진 URL은 null입니다.
 *
 * @param inspectionId 점검 기록 id
 * @param kind 점검 종류 (ROUTINE · FORECAST · SUBSTITUTE)
 * @param createdAt 기록 생성 시각
 * @param review 검토 상태 (NOT_REQUIRED · PENDING · APPROVED · REJECTED)
 * @param beforePhotoUrl 점검 전 사진 조회 URL (사진이 삭제됐으면 null)
 * @param afterPhotoUrl 청소 후 사진 조회 URL (청소 전 · 사진이 삭제됐으면 null)
 * @param beforeGrade AI가 판정한 점검 전 등급 (0 괜찮음 · 2 쌓임 · 3 막힘)
 * @param afterGrade AI가 판정한 청소 후 등급 (0 괜찮음 · 2 쌓임 · 3 막힘, 청소 전이면 null)
 * @param cleaned 청소 후 사진을 낸 기록인지
 * @param xp 이 점검으로 지급한 경험치
 * @param photoDeleted 사진 보존 기간이 지나 사진을 내려주지 않는지
 */
public record InspectionSummaryResponse(
        Long inspectionId,
        InspectionKind kind,
        LocalDateTime createdAt,
        ReviewStatus review,
        String beforePhotoUrl,
        String afterPhotoUrl,
        Integer beforeGrade,
        Integer afterGrade,
        boolean cleaned,
        Integer xp,
        boolean photoDeleted
) {
}
