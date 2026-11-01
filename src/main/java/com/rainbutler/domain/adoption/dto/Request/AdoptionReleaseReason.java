package com.rainbutler.domain.adoption.dto.Request;

import com.rainbutler.domain.adoption.entity.ReleaseReason;

/**
 * 사용자가 직접 선택할 수 있는 파양 사유입니다.
 *
 * <p>{@link ReleaseReason}에는 자동 파양({@code AUTO}) · 회원 탈퇴({@code WITHDRAWN})도 있지만,
 * 이 API에서는 사용자가 고를 수 없습니다.
 */
public enum AdoptionReleaseReason {

    /** 이사 */
    MOVED(ReleaseReason.MOVED),
    /** 시간 부족 */
    NO_TIME(ReleaseReason.NO_TIME),
    /** 기타 */
    OTHER(ReleaseReason.OTHER);

    private final ReleaseReason releaseReason;

    AdoptionReleaseReason(ReleaseReason releaseReason) {
        this.releaseReason = releaseReason;
    }

    /**
     * 저장할 파양 사유로 바꿉니다.
     *
     * @return 파양 사유
     */
    public ReleaseReason toReleaseReason() {
        return releaseReason;
    }
}
