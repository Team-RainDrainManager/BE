package com.rainbutler.domain.drain.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 지도 · 목록에서 보여주는 빗물받이 표시 상태입니다.
 *
 * <p>DB에 저장하지 않고 조회할 때마다 계산합니다. 우선순위는 막힘 제보 → 미입양 → 점검 지연 → 정상입니다.
 */
@Getter
@RequiredArgsConstructor
public enum DrainDisplayStatus {
    /** 입양되지 않음 */
    AVAILABLE("미입양"),
    /** 입양되어 있고 다음 점검일이 지나지 않음 */
    NORMAL("정상"),
    /** 입양되어 있지만 다음 점검일이 지남 */
    MANAGEMENT_DELAYED("점검 지연"),
    /** 처리 대기 중인 막힘 제보가 있음 */
    BLOCKAGE_REPORTED("막힘 제보");

    private final String label;
}
