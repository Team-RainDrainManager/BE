package com.rainbutler.domain.sanction.dto;

import com.rainbutler.domain.sanction.entity.Sanction;
import java.time.LocalDateTime;

/**
 * 제재 내역 항목입니다.
 *
 * @param sanctionId 제재 id
 * @param createdAt 부과 시각
 * @param startsAt 시작 시각 (제재 중에 추가로 부과되면 기존 종료 시각부터)
 * @param until 종료 시각 (영구면 null)
 * @param permanent 영구 제재 여부
 * @param active 지금 효력이 있는지
 * @param reason 제재 사유
 * @param revokedAt 관리자 해제 시각 (해제 안 됐으면 null)
 */
public record SanctionResponse(
        Long sanctionId,
        LocalDateTime createdAt,
        LocalDateTime startsAt,
        LocalDateTime until,
        boolean permanent,
        boolean active,
        String reason,
        LocalDateTime revokedAt) {

    /**
     * 제재 엔티티로 응답을 만듭니다.
     *
     * @param sanction 제재
     * @param now 효력 판단 기준 시각
     */
    public static SanctionResponse of(Sanction sanction, LocalDateTime now) {
        return new SanctionResponse(sanction.getId(), sanction.getCreatedAt(),
                sanction.getStartsAt(), sanction.getUntil(), sanction.isPermanent(),
                sanction.isActive(now), sanction.getReason(), sanction.getRevokedAt());
    }
}
