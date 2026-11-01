package com.rainbutler.domain.adoption.dto.Request;

import jakarta.validation.constraints.NotNull;

/**
 * 입양 해제 요청입니다.
 *
 * @param reason 파양 사유 (MOVED · NO_TIME · OTHER)
 */
public record AdoptionReleaseRequest(
        @NotNull(message = "파양 사유를 선택해 주세요.")
        AdoptionReleaseReason reason
) {
}
