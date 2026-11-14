package com.rainbutler.domain.report.dto;

import com.rainbutler.domain.sanction.entity.Sanction;
import java.time.LocalDateTime;

/**
 * 이 제보로 부과된 제재입니다. (허위 제보 반려)
 *
 * @param sanctionId 제재 id
 * @param startsAt 시작 시각
 * @param until 종료 시각 (영구면 null)
 * @param permanent 영구 제재 여부
 */
public record ReportSanctionResponse(
        Long sanctionId, LocalDateTime startsAt, LocalDateTime until, boolean permanent) {

    /**
     * 제재로 응답을 만듭니다.
     *
     * @param sanction 제재 (없으면 null)
     * @return 응답 (없으면 null)
     */
    public static ReportSanctionResponse from(Sanction sanction) {
        if (sanction == null) {
            return null;
        }
        return new ReportSanctionResponse(sanction.getId(), sanction.getStartsAt(),
                sanction.getUntil(), sanction.isPermanent());
    }
}
