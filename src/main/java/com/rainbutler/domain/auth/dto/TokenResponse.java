package com.rainbutler.domain.auth.dto;

/**
 * 토큰 발급 응답입니다.
 *
 * @param accessToken access 토큰 (Authorization: Bearer 헤더에 넣는다)
 * @param refreshToken refresh 토큰 (재발급 때 body로 보낸다)
 * @param accessTokenExpiresIn access 토큰 유효 기간 (초)
 * @param tokenType 토큰 종류 (항상 "Bearer")
 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        long accessTokenExpiresIn,
        String tokenType) {

    private static final String BEARER = "Bearer";

    /**
     * Bearer 토큰 응답을 만듭니다.
     */
    public static TokenResponse bearer(String accessToken, String refreshToken, long expiresIn) {
        return new TokenResponse(accessToken, refreshToken, expiresIn, BEARER);
    }
}
