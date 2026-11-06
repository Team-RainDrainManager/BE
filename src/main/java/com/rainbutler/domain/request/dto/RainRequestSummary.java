package com.rainbutler.domain.request.dto;

/**
 * 빗물받이의 이번 비 대신 점검 요청 요약입니다. 공개 API라 입양자 · 수락자 정보는 넣지 않습니다.
 *
 * @param requestId 요청 id
 * @param status 지도 상태 (NEEDS_HELP · IN_PROGRESS · DONE)
 */
public record RainRequestSummary(Long requestId, RainRequestStatus status) {
}
