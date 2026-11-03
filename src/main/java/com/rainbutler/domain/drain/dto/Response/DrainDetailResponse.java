package com.rainbutler.domain.drain.dto.Response;

import com.rainbutler.domain.drain.entity.DrainDisplayStatus;
import java.time.LocalDateTime;

/**
 * 빗물받이 상세입니다.
 *
 * @param id 빗물받이 id
 * @param name 표시 이름 (입양 별명 → 등록 이름 → 빗물받이 #id)
 * @param address 주소 (없으면 null)
 * @param status 운영 상태 (ACTIVE · PENDING · REMOVED)
 * @param adoptionStatus 입양 상태 (AVAILABLE · ADOPTED)
 * @param displayStatus 화면 표시 상태
 * @param displayStatusLabel 화면 표시 상태의 한국어 라벨
 * @param latitude 위도
 * @param longitude 경도
 * @param photoUrl 최신 현장 사진 URL
 * @param dirtLevel 최신 더러움 단계 (1~3)
 * @param isMine 로그인 사용자가 입양한 빗물받이인지 (비로그인은 false)
 * @param level 성장 레벨 (입양 전 0)
 * @param levelTitle 레벨 칭호 (입양 전 미입양)
 * @param xp 누적 성장 점수 (입양 전 0)
 * @param nextLevelScore 다음 레벨이 되는 누적 점수 (입양 전 0)
 * @param progressPercent 현재 레벨 구간 진행률 (입양 전 0)
 * @param adoption 현재 ACTIVE 입양 (없으면 null)
 */
public record DrainDetailResponse(
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
        boolean isMine,
        Integer level,
        String levelTitle,
        Integer xp,
        Integer nextLevelScore,
        Integer progressPercent,
        AdoptionResponse adoption
) {
    public record AdoptionResponse(
            Long adoptionId,
            String nickname,
            Integer level,
            Integer score,
            LocalDateTime adoptedAt,
            LocalDateTime lastManagedAt,
            LocalDateTime nextManagementAt
    ) {
    }
}
