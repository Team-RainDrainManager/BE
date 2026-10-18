package com.rainbutler.domain.user.dto;

/**
 * 점검 쉬기를 신청할 수 없는 이유입니다.
 */
public enum PauseUnavailableReason {
    /** 이미 쉬는 중 */
    ALREADY_ACTIVE,
    /** 입양 중인 빗물받이가 없음 */
    NO_ADOPTION,
    /** 마지막 신청 후 쿨다운(기본 90일)이 지나지 않음 */
    COOLDOWN
}
