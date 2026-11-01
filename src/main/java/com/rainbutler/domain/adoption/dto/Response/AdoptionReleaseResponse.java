package com.rainbutler.domain.adoption.dto.Response;

import java.time.LocalDateTime;

/**
 * 입양 해제 응답입니다.
 *
 * @param adoptionId 해제한 입양 id
 * @param releasedAt 해제 시각
 */
public record AdoptionReleaseResponse(
        Long adoptionId,
        LocalDateTime releasedAt
) {
}
