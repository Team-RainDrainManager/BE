package com.rainbutler.domain.adoption.dto.Response;

import com.rainbutler.domain.drain.entity.DrainDisplayStatus;
import java.time.LocalDateTime;

/**
 * 입양 상세입니다. (화면 06)
 *
 * <p>내 입양 목록({@link MyAdoptionResponse})의 돌봄 정보에 빗물받이 정보와 입양일을 더합니다.
 *
 * @param adoptionId 입양 id
 * @param nickname 빗물받이 별명
 * @param adoptedAt 입양 시각
 * @param level 캐릭터 레벨
 * @param score 누적 성장 점수
 * @param levelTitle 레벨 칭호
 * @param nextLevelScore 다음 레벨이 되는 누적 점수 (만렙이면 현재 점수)
 * @param progressPercent 현재 레벨 구간 진행률 (0~100)
 * @param lastManagedAt 마지막 돌봄 시각 (입양일 · 마지막 승인 점검 중 늦은 시각)
 * @param nextManagementAt 다음 관리 예정 시각
 * @param daysUntilNextManagement 다음 관리 예정일까지 남은 일수 (지났으면 음수, 예정일이 없으면 null)
 * @param releaseWarning 28일 이상 돌봄 기록이 없어 자동 해제 전 경고가 필요한지
 * @param missedForecastCount 연속 비 예보 요청 무응답 횟수
 * @param lonely 외로운 또랑이 표시 대상인지
 * @param displayStatus 화면 표시 상태
 * @param displayStatusLabel 화면 표시 상태의 한국어 라벨
 * @param drain 빗물받이 정보
 */
public record AdoptionDetailResponse(
        Long adoptionId,
        String nickname,
        LocalDateTime adoptedAt,
        Integer level,
        Integer score,
        String levelTitle,
        Integer nextLevelScore,
        Integer progressPercent,
        LocalDateTime lastManagedAt,
        LocalDateTime nextManagementAt,
        Integer daysUntilNextManagement,
        boolean releaseWarning,
        Integer missedForecastCount,
        boolean lonely,
        DrainDisplayStatus displayStatus,
        String displayStatusLabel,
        DrainInfo drain
) {

    /**
     * 입양 상세에 함께 내려주는 빗물받이 정보입니다.
     *
     * @param drainId 빗물받이 id
     * @param name 등록 이름 (없으면 null)
     * @param address 주소 (없으면 null)
     * @param latitude 위도
     * @param longitude 경도
     * @param photoUrl 최신 현장 사진 조회 URL
     * @param dirtLevel 최신 더러움 단계 (1~3)
     * @param risk 위험 빗물받이인지
     */
    public record DrainInfo(
            Long drainId,
            String name,
            String address,
            Double latitude,
            Double longitude,
            String photoUrl,
            Integer dirtLevel,
            boolean risk
    ) {
    }
}
