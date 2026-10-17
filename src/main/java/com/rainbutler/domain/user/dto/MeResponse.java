package com.rainbutler.domain.user.dto;

import com.rainbutler.domain.user.entity.Provider;
import com.rainbutler.domain.user.entity.Role;
import com.rainbutler.domain.user.entity.User;

/**
 * 내 정보 응답입니다.
 *
 * @param id 사용자 id
 * @param nickname 닉네임
 * @param email 이메일 (카카오 사용자는 null)
 * @param profileImage 프로필 사진 URL
 * @param role 역할
 * @param provider 로그인 방식
 * @param point 포인트
 * @param level 활동 레벨
 * @param onboardingCompleted 온보딩(약관 동의 + 본인인증) 완료 여부
 * @param identityVerified 본인인증 여부
 */
public record MeResponse(
        Long id,
        String nickname,
        String email,
        String profileImage,
        Role role,
        Provider provider,
        Integer point,
        Integer level,
        boolean onboardingCompleted,
        boolean identityVerified) {

    /**
     * 사용자 엔티티로 응답을 만듭니다.
     */
    public static MeResponse from(User user) {
        return new MeResponse(user.getId(), user.getNickname(), user.getEmail(),
                user.getProfileImage(), user.getRole(), user.getProvider(), user.getPoint(),
                user.getLevel(), user.isOnboardingCompleted(), user.isIdentityVerified());
    }
}
