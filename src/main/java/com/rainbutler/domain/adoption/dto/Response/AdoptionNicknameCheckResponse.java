package com.rainbutler.domain.adoption.dto.Response;

/**
 * 입양 빗물받이 별명 중복 확인 응답입니다.
 *
 * @param nickname 확인한 별명 (앞뒤 공백 제거본)
 * @param available 사용할 수 있는지. {@code false}면 이미 다른 입양이 쓰는 별명이다
 */
public record AdoptionNicknameCheckResponse(
        String nickname,
        boolean available
) {
}
