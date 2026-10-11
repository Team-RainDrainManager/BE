package com.rainbutler.domain.auth.dto;

/**
 * 카카오 로그인 응답입니다. 토큰과 함께 신규 가입 여부를 알려줍니다.
 *
 * @param accessToken access 토큰
 * @param refreshToken refresh 토큰
 * @param accessTokenExpiresIn access 토큰 유효 기간 (초)
 * @param tokenType 토큰 종류 (항상 "Bearer")
 * @param isNewUser 이번 로그인으로 새로 가입했는지 (온보딩 화면 분기용)
 */
public record KakaoLoginResponse(
        String accessToken,
        String refreshToken,
        long accessTokenExpiresIn,
        String tokenType,
        boolean isNewUser) {

    /**
     * 토큰 응답에 신규 가입 여부를 붙입니다.
     */
    public static KakaoLoginResponse of(TokenResponse token, boolean isNewUser) {
        return new KakaoLoginResponse(token.accessToken(), token.refreshToken(),
                token.accessTokenExpiresIn(), token.tokenType(), isNewUser);
    }
}
