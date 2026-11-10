package com.rainbutler.domain.request.dto;

import com.rainbutler.domain.request.entity.InspectionRequest;
import com.rainbutler.domain.request.entity.RequestStatus;
import java.time.LocalDateTime;

/**
 * 응답 결과입니다.
 *
 * <p>"가능"이면 프론트가 "고마워요! {마감}까지 살펴봐 주세요. 근처 {반경}m에 도움이 필요한 빗물받이가 {개수}곳
 * 있어요 [지도에서 보기]"를 보여 줄 수 있게 마감 · 근처 개수 · 반경을 함께 내려줍니다.
 *
 * @param requestId 요청 id
 * @param state 바뀐 상태 (ACCEPTED · OPEN)
 * @param repliedAt 응답 시각
 * @param completeDeadlineAt 완료 마감
 * @param nearbyOpenCount 내 빗물받이 근처에서 지금 대신 점검이 필요한 빗물받이 수 (불가 응답이면 null)
 * @param nearbyRadiusMeters 근처 개수를 센 반경 (m, 불가 응답이면 null)
 */
public record RequestReplyResponse(
        Long requestId,
        RequestStatus state,
        LocalDateTime repliedAt,
        LocalDateTime completeDeadlineAt,
        Integer nearbyOpenCount,
        Integer nearbyRadiusMeters) {

    /**
     * 요청과 근처 개수로 응답을 만듭니다.
     *
     * @param request 응답한 요청
     * @param nearbyOpenCount 근처 도움 필요 개수 (불가면 null)
     * @param nearbyRadiusMeters 반경 (불가면 null)
     */
    public static RequestReplyResponse of(
            InspectionRequest request, Integer nearbyOpenCount, Integer nearbyRadiusMeters) {
        return new RequestReplyResponse(request.getId(), request.getStatus(),
                request.getRepliedAt(), request.getCompleteDeadlineAt(), nearbyOpenCount,
                nearbyRadiusMeters);
    }
}
