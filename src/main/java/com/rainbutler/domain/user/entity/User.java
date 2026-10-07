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
 * 사용자 (카카오 로그인)입니다. (users 테이블)
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

    /** 소셜 로그인 제공자 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Provider provider;

    /** 카카오 회원번호 */
    @Column(nullable = false)
    private String providerId;

    @Column(nullable = false)
    private String nickname;

    /** 실명 (봉사 확인서용) */
    private String realName;

    /** 실명 인증 시각 */
    private LocalDateTime identityVerifiedAt;

    private String email;

    private String profileImage;

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

    /** 필수 값으로 생성합니다. 초기 상태·기본값은 여기서 채웁니다. */
    @Builder
    private User(Provider provider, String providerId, String nickname, Role role) {
        this.provider = provider;
        this.providerId = providerId;
        this.nickname = nickname;
        this.role = role;
        this.point = 0;
        this.level = 1;
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
