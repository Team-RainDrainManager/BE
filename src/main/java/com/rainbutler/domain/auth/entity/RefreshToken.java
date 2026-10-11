package com.rainbutler.domain.auth.entity;

import com.rainbutler.domain.user.entity.User;
import com.rainbutler.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

/**
 * refresh 토큰입니다. (refresh_token 테이블)
 *
 * <p>원문은 클라이언트만 가지고, DB에는 SHA-256 해시만 저장합니다. 재발급할 때마다 회전하며,
 * 이미 폐기된 토큰이 다시 오면 탈취로 보고 해당 사용자의 토큰을 전부 폐기합니다.
 */
@Entity
@Table(name = "refresh_token")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** refresh 토큰 SHA-256 (hex 64자) */
    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    /** 폐기 시각 (회전 · 로그아웃 · 탈취 감지) */
    private LocalDateTime revokedAt;

    /** 필수 값으로 생성합니다. */
    @Builder
    private RefreshToken(User user, String tokenHash, LocalDateTime expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    /**
     * 토큰을 폐기합니다. 이미 폐기된 토큰이면 처음 폐기 시각을 유지합니다.
     */
    public void revoke() {
        if (this.revokedAt == null) {
            this.revokedAt = LocalDateTime.now();
        }
    }

    /**
     * 만료됐는지 확인합니다.
     *
     * @param now 기준 시각
     * @return 만료 여부
     */
    public boolean isExpired(LocalDateTime now) {
        return !now.isBefore(this.expiresAt);
    }

    /**
     * 폐기됐는지 확인합니다.
     *
     * @return 폐기 여부
     */
    public boolean isRevoked() {
        return this.revokedAt != null;
    }
}
