package com.rainbutler.domain.report.dto;

import com.rainbutler.domain.report.entity.Report;
import com.rainbutler.domain.report.entity.ReportStatus;
import com.rainbutler.domain.report.entity.ReportType;
import com.rainbutler.domain.sanction.entity.Sanction;
import java.time.LocalDateTime;

/**
 * 제보 상세 응답입니다.
 *
 * @param reportId 제보 id
 * @param drainId 빗물받이 id
 * @param type 제보 종류
 * @param description 제보 설명
 * @param photoUrl 사진 URL
 * @param latitude 촬영 위치 위도
 * @param longitude 촬영 위치 경도
 * @param newLatitude 새 위치 위도 (LOCATION_FIX인 경우)
 * @param newLongitude 새 위치 경도 (LOCATION_FIX인 경우)
 * @param status 처리 상태
 * @param blocksAdoption 이 제보가 빗물받이 입양을 막는지
 * @param adminComment 관리자 코멘트
 * @param resolvedAt 처리 완료 시각
 * @param clearedAt 입양 차단 해제 시각 (해제 전에는 null)
 * @param createdAt 제보 생성 시각
 * @param sanction 이 제보로 부과된 제재 (허위 제보 반려. 없으면 null)
 */
public record ReportDetailResponse(
        Long reportId,
        Long drainId,
        ReportType type,
        String description,
        String photoUrl,
        Double latitude,
        Double longitude,
        Double newLatitude,
        Double newLongitude,
        ReportStatus status,
        boolean blocksAdoption,
        String adminComment,
        LocalDateTime resolvedAt,
        LocalDateTime clearedAt,
        LocalDateTime createdAt,
        ReportSanctionResponse sanction
) {

    /**
     * 제보 엔티티를 상세 응답으로 바꿉니다.
     *
     * @param report 제보
     * @param photoUrl 조회 URL로 바꾼 사진 (사진이 없으면 null)
     * @param sanction 이 제보로 부과된 제재 (없으면 null)
     * @return 제보 상세 응답
     */
    public static ReportDetailResponse from(Report report, String photoUrl, Sanction sanction) {
        return new ReportDetailResponse(
                report.getId(),
                report.getDrain() == null ? null : report.getDrain().getId(),
                report.getType(),
                report.getDescription(),
                photoUrl,
                report.getLatitude(),
                report.getLongitude(),
                report.getNewLatitude(),
                report.getNewLongitude(),
                report.getStatus(),
                report.isBlocksAdoption(),
                report.getAdminComment(),
                report.getResolvedAt(),
                report.getClearedAt(),
                report.getCreatedAt(),
                ReportSanctionResponse.from(sanction));
    }
}
