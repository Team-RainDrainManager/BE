package com.rainbutler.domain.drain.dto.Response;

import com.rainbutler.domain.drain.entity.DrainDisplayStatus;

/**
 * 빗물받이 생성 응답입니다.
 *
 * @param id 생성된 빗물받이 id
 * @param name 표시 이름 (요청 이름이 없으면 빗물받이 #id)
 * @param address 주소 (요청에 없고 역지오코딩도 실패하면 null)
 * @param status 운영 상태 (생성 시 ACTIVE)
 * @param adoptionStatus 입양 상태 (생성 시 AVAILABLE)
 * @param displayStatus 화면 표시 상태 (검토용 제보를 만들었으면 막힘 제보)
 * @param displayStatusLabel 화면 표시 상태의 한국어 라벨
 * @param latitude 위도
 * @param longitude 경도
 * @param photoUrl 현장 사진 URL
 * @param dirtLevel 서버 AI 판정 더러움 단계 (1~3)
 * @param isRisk 서버 AI 판정 위험 여부 (이 값으로 저장됨)
 * @param aiConfidence AI 확신도 (0~1)
 * @param riskRemind 사용자 체크값과 AI 판정이 다르거나 AI 확신도가 낮아 리마인드가 필요한지
 * @param riskReportId 검토 사유로 생성된 관리자 검토용 제보 id (없으면 null)
 * @param adoptionBlocked AI가 청소 불가로 판정해 입양이 막혔는지
 */
public record DrainCreateResponse(
        Long id,
        String name,
        String address,
        String status,
        String adoptionStatus,
        DrainDisplayStatus displayStatus,
        String displayStatusLabel,
        Double latitude,
        Double longitude,
        String photoUrl,
        Integer dirtLevel,
        boolean isRisk,
        Double aiConfidence,
        boolean riskRemind,
        Long riskReportId,
        boolean adoptionBlocked
) {
}
