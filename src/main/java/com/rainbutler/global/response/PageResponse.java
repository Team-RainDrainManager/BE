package com.rainbutler.global.response;

import java.util.List;

/**
 * 페이지네이션 응답입니다. (0부터 시작하는 page)
 *
 * @param content 현재 페이지 항목
 * @param page 현재 페이지 번호 (0부터)
 * @param size 페이지 크기
 * @param totalElements 전체 항목 수
 * @param totalPages 전체 페이지 수
 * @param hasNext 다음 페이지가 있는지
 * @param <T> 항목 타입
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    /**
     * Spring Data 페이지를 공통 응답으로 바꿉니다.
     */
    public static <T> PageResponse<T> of(
            List<T> content, int page, int size, long totalElements, int totalPages) {
        return new PageResponse<>(
                content, page, size, totalElements, totalPages, page + 1 < totalPages);
    }
}
