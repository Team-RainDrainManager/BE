package com.rainbutler.domain.review.dto;

import java.util.List;

/**
 * 검토 대기 목록입니다.
 *
 * @param reviews 최신순 검토 대기 목록
 */
public record ReviewListResponse(List<ReviewItemResponse> reviews) {
}
