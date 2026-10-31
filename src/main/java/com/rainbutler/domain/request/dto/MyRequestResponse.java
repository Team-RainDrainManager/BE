package com.rainbutler.domain.request.dto;

import com.rainbutler.domain.request.entity.RequestStatus;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 내 비 예보 요청 목록의 한 건입니다.
 *
 * @param requestId 요청 id
 * @param drain 빗물받이
 * @param state 요청 상태
 * @param role 나와의 관계 (OWNER 내 또랑이 · CLAIMANT 내가 맡음 · NEARBY 근처 대신 점검)
 * @param rainStartAt 비 시작 예상 시각
 * @param replyDeadlineAt 응답 마감
 * @param deadlineAt 완료 마감
 * @param remainingMinutes 다음 마감까지 남은 분 (응답 전이면 응답 마감, 아니면 완료 마감. 끝난 요청은 null)
 * @param distanceM 보낸 위치에서 빗물받이까지 거리 (위치를 보내지 않았으면 null)
 * @param availableActions 지금 할 수 있는 행동 (REPLY · INSPECT · CLAIM)
 */
public record MyRequestResponse(
        Long requestId,
        DrainSummary drain,
        RequestStatus state,
        Role role,
        LocalDateTime rainStartAt,
        LocalDateTime replyDeadlineAt,
        LocalDateTime deadlineAt,
        Long remainingMinutes,
        Integer distanceM,
        List<Action> availableActions) {

    /** 나와의 관계입니다. */
    public enum Role {
        /** 내 또랑이의 요청 */
        OWNER,
        /** 내가 대신 점검으로 맡은 요청 */
        CLAIMANT,
        /** 근처에서 대신 점검을 기다리는 요청 */
        NEARBY
    }

    /** 지금 할 수 있는 행동입니다. */
    public enum Action {
        /** 가능 · 불가 응답 */
        REPLY,
        /** 점검하기 */
        INSPECT,
        /** 대신 점검 맡기 */
        CLAIM
    }

    /**
     * 빗물받이 요약입니다.
     *
     * @param drainId 빗물받이 id
     * @param name 입양된 빗물받이면 입양 이름, 아니면 null
     * @param latitude 위도
     * @param longitude 경도
     */
    public record DrainSummary(Long drainId, String name, Double latitude, Double longitude) {
    }
}
