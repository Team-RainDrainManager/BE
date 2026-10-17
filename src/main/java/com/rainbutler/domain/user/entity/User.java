package com.rainbutler.domain.user.entity;

import com.rainbutler.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

/**
 * 사용자입니다. (users 테이블)
 *
 * <p>자체 로그인(LOCAL)과 카카오 로그인(KAKAO)은 같은 이메일이어도 별개 계정입니다.
 * 생성은 {@link #local}, {@link #kakao} 정적 팩토리로만 합니다.
 */
@Entity
@Table(name = "users")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 로그인 제공자 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Provider provider;

    /** 카카오 회원번호 (자체 로그인은 null) */
    private String providerId;

    @Column(nullable = false)
    private String nickname;

    /** 실명 (봉사 확인서용) */
    private String realName;

    /** 실명 인증 시각 */
    private LocalDateTime identityVerifiedAt;

    private String email;

    private String profileImage;

    /** BCrypt 해시 (자체 로그인만) */
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /** point_history 합계 */
    @Column(nullable = false)
    private Integer point;

    /** 사용자 전체 활동 레벨 */
    @Column(nullable = false)
    private Integer level;

    /** 활동 위치 - 대신 점검 대상 판정 */
    private Double latitude;

    private Double longitude;

    /** 활동 위치 갱신 시각 */
    private LocalDateTime locationUpdatedAt;

    /** 온보딩(약관 동의 + 본인인증) 완료 시각. null이면 미완료 */
    private LocalDateTime onboardingCompletedAt;

    /** 활동 알림 수신 동의 (동의 전에는 알림을 보내지 않는다) */
    @Column(nullable = false)
    private boolean notificationEnabled;

    /** 야간(22시~08시) 푸시 수신 동의 */
    @Column(nullable = false)
    private boolean nightPushConsent;

    /** 정적 팩토리에서만 사용합니다. 초기 상태·기본값은 여기서 채웁니다. */
    @Builder(access = AccessLevel.PRIVATE)
    private User(
            Provider provider,
            String providerId,
            String email,
            String password,
            String nickname,
            String profileImage) {
        this.provider = provider;
        this.providerId = providerId;
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.profileImage = profileImage;
        // 신규 가입은 항상 USER. ADMIN · CITY_OFFICIAL은 DB에서 직접 지정한다
        this.role = Role.USER;
        this.point = 0;
        this.level = 1;
    }

    /**
     * 이메일 · 비밀번호로 가입한 자체 로그인 사용자를 만듭니다.
     *
     * @param email 이메일 (소문자로 정규화된 값)
     * @param encodedPassword BCrypt로 인코딩된 비밀번호
     * @param nickname 닉네임
     * @return 새 사용자
     */
    public static User local(String email, String encodedPassword, String nickname) {
        return User.builder()
                .provider(Provider.LOCAL)
                .email(email)
                .password(encodedPassword)
                .nickname(nickname)
                .build();
    }

    /**
     * 카카오 로그인으로 가입한 사용자를 만듭니다.
     *
     * @param kakaoId 카카오 회원번호
     * @param nickname 닉네임
     * @param profileImage 프로필 사진 URL (없으면 null)
     * @return 새 사용자
     */
    public static User kakao(String kakaoId, String nickname, String profileImage) {
        return User.builder()
                .provider(Provider.KAKAO)
                .providerId(kakaoId)
                .nickname(nickname)
                .profileImage(profileImage)
                .build();
    }

    /**
     * 활동 위치를 갱신합니다. 대신 점검 대상 판정에 사용합니다.
     *
     * @param latitude 위도
     * @param longitude 경도
     */
    public void updateLocation(Double latitude, Double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.locationUpdatedAt = LocalDateTime.now();
    }
}
