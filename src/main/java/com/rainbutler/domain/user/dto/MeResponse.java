package com.rainbutler.domain.user.dto;

import com.rainbutler.domain.user.entity.Provider;
import com.rainbutler.domain.user.entity.Role;
import com.rainbutler.domain.user.entity.User;
import java.time.LocalDateTime;

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
 * @param participationScore 참여 점수 (포인트 이력의 적립 합 − 회수 합)
 * @param adoptionCount 입양 중인 빗물받이 수
 * @param sanction 제재 상태
 * @param pause 점검 쉬기 상태
 * @param inspectionCount 인정된 점검 수 (정기 · 비 예보 · 대신 점검)
 * @param volunteerMinutes 봉사 시간 (분, 청소 1회당 정책 값만큼)
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
        boolean identityVerified,
        long participationScore,
        long adoptionCount,
        SanctionSummary sanction,
        PauseSummary pause,
        long inspectionCount,
        long volunteerMinutes) {

    /**
     * 사용자 엔티티와 상태 정보로 응답을 만듭니다.
     */
    public static MeResponse of(User user, long participationScore, long adoptionCount,
            SanctionSummary sanction, PauseSummary pause, long inspectionCount,
            long volunteerMinutes) {
        return new MeResponse(user.getId(), user.getNickname(), user.getEmail(),
                user.getProfileImage(), user.getRole(), user.getProvider(), user.getPoint(),
                user.getLevel(), user.isOnboardingCompleted(), user.isIdentityVerified(),
                participationScore, adoptionCount, sanction, pause, inspectionCount,
                volunteerMinutes);
    }

    /**
     * 제재 상태입니다.
     *
     * @param active 지금 제재 중인지
     * @param until 제재 종료 시각 (제재 중이 아니거나 영구면 null)
     * @param permanent 영구 제재인지
     */
    public record SanctionSummary(boolean active, LocalDateTime until, boolean permanent) {

        /** 제재 중이 아님 */
        public static final SanctionSummary NONE = new SanctionSummary(false, null, false);
    }

    /**
     * 점검 쉬기 상태입니다.
     *
     * @param activeUntil 쉬는 중이면 종료 시각 (아니면 null)
     * @param nextAvailableAt 쿨다운 중이면 다음 신청 가능 시각 (아니면 null)
     */
    public record PauseSummary(LocalDateTime activeUntil, LocalDateTime nextAvailableAt) {
    }
}
