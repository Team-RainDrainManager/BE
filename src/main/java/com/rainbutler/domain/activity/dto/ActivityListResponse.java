package com.rainbutler.domain.activity.dto;

import java.util.List;

/**
 * 활동 내역 한 페이지입니다.
 *
 * @param nextCursor 다음 페이지 요청에 보낼 cursor (다음 페이지가 없으면 null)
 * @param activities 최신순 활동 내역
 */
public record ActivityListResponse(String nextCursor, List<ActivityResponse> activities) {
}
