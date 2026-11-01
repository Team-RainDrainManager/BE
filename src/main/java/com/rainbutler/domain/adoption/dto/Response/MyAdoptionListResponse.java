package com.rainbutler.domain.adoption.dto.Response;

import java.util.List;

/**
 * 로그인 사용자의 현재 입양 목록입니다.
 *
 * @param count 현재 ACTIVE 입양 수
 * @param limit 사용자당 최대 입양 수
 * @param adoptions 현재 입양 목록
 */
public record MyAdoptionListResponse(
        int count,
        int limit,
        List<MyAdoptionResponse> adoptions
) {
}
