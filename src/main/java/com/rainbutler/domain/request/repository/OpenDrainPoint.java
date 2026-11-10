package com.rainbutler.domain.request.repository;

/**
 * 지금 도움이 필요한(완료 마감 전 OPEN 요청이 있는) 빗물받이의 id · 위치입니다. (근처 도움 필요 개수 조회 결과)
 *
 * @param drainId 빗물받이 id
 * @param latitude 위도
 * @param longitude 경도
 */
public record OpenDrainPoint(Long drainId, Double latitude, Double longitude) {
}
