package com.rainbutler.domain.request.dto;

import com.rainbutler.domain.request.entity.InspectionRequest;
import com.rainbutler.domain.request.entity.RequestStatus;
import java.time.LocalDateTime;

/**
 * 대신 점검 수락 결과입니다.
 *
 * @param requestId 요청 id
 * @param state 상태 (CLAIMED)
 * @param claimedAt 수락 시각
 * @param completeDeadlineAt 완료 마감
 */
public record RequestClaimResponse(
        Long requestId, RequestStatus state, LocalDateTime claimedAt,
        LocalDateTime completeDeadlineAt) {

    /**
     * 요청으로 응답을 만듭니다.
     */
    public static RequestClaimResponse from(InspectionRequest request) {
        return new RequestClaimResponse(request.getId(), request.getStatus(),
                request.getClaimedAt(), request.getCompleteDeadlineAt());
    }
}
