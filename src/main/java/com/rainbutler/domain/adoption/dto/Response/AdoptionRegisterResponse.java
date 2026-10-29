package com.rainbutler.domain.adoption.dto.Response;

import java.time.LocalDateTime;

/**
 * 입양 등록 응답입니다.
 *
 * @param adoptionId 입양 id
 * @param drainId 빗물받이 id
 * @param nickname 빗물받이 별명
 * @param adoptedAt 입양 일시
 * @param nextManagementAt 다음 관리 예정일 (+14일)
 */
public record AdoptionRegisterResponse(
        Long adoptionId,
        Long drainId,
        String nickname,
        LocalDateTime adoptedAt,
        LocalDateTime nextManagementAt
) {
}
