package com.rainbutler.domain.request.dto;

import com.rainbutler.domain.request.entity.InspectionRequest;
import com.rainbutler.domain.request.entity.RequestStatus;
import java.time.LocalDateTime;

/**
 * 응답 결과입니다.
 *
 * @param requestId 요청 id
 * @param state 바뀐 상태 (ACCEPTED · OPEN)
 * @param repliedAt 응답 시각
 */
public record RequestReplyResponse(Long requestId, RequestStatus state, LocalDateTime repliedAt) {

    /**
     * 요청으로 응답을 만듭니다.
     */
    public static RequestReplyResponse from(InspectionRequest request) {
        return new RequestReplyResponse(
                request.getId(), request.getStatus(), request.getRepliedAt());
    }
}
