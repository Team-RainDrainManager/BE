package com.rainbutler.domain.auth.client;

/**
 * 카카오에서 받은 사용자 정보 중 우리가 쓰는 값입니다.
 *
 * @param kakaoId 카카오 회원번호
 * @param nickname 닉네임 (없으면 "집사" + 4자리 숫자)
 * @param profileImageUrl 프로필 사진 URL (동의하지 않았으면 null)
 */
public record KakaoUserInfo(String kakaoId, String nickname, String profileImageUrl) {
}
