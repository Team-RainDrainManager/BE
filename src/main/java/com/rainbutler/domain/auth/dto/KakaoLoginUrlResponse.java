package com.rainbutler.domain.auth.dto;

/**
 * 카카오 인가 페이지 주소 응답입니다.
 *
 * @param url 카카오 인가 페이지 URL
 */
public record KakaoLoginUrlResponse(String url) {
}
