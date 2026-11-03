package com.rainbutler.domain.report.dto;

import com.rainbutler.domain.report.entity.Report;
import com.rainbutler.domain.report.entity.ReportStatus;
import com.rainbutler.domain.report.entity.ReportType;
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
 * @param adminComment 관리자 코멘트
 * @param resolvedAt 처리 완료 시각
 * @param createdAt 제보 생성 시각
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
        String adminComment,
        LocalDateTime resolvedAt,
        LocalDateTime createdAt
) {

    /**
     * 제보 엔티티를 상세 응답으로 바꿉니다.
     *
     * @param report 제보
     * @param photoUrl 조회 URL로 바꾼 사진 (사진이 없으면 null)
     * @return 제보 상세 응답
     */
    public static ReportDetailResponse from(Report report, String photoUrl) {
        return new ReportDetailResponse(
                report.getId(),
                report.getDrain().getId(),
                report.getType(),
                report.getDescription(),
                photoUrl,
                report.getLatitude(),
                report.getLongitude(),
                report.getNewLatitude(),
                report.getNewLongitude(),
                report.getStatus(),
                report.getAdminComment(),
                report.getResolvedAt(),
                report.getCreatedAt());
    }
}
