package com.rainbutler.domain.push.entity;

import com.rainbutler.domain.user.entity.User;
import com.rainbutler.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * FCM 기기 토큰입니다. (fcm_token 테이블)
 *
 * <p>사용자당 여러 기기를 등록할 수 있습니다. 토큰은 소프트 삭제하지 않고 하드 삭제합니다.
 * (소프트 삭제하면 같은 토큰 재등록이 unique 제약에 걸립니다) {@code deleted_at}은 BaseEntity 때문에 있지만
 * 쓰지 않습니다.
 */
@Entity
@Table(name = "fcm_token")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FcmToken extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** FCM 등록 토큰 */
    @Column(nullable = false, unique = true, columnDefinition = "text")
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FcmPlatform platform;

    /** 마지막 등록 · 갱신 시각 */
    @Column(nullable = false)
    private LocalDateTime lastUsedAt;

    /** 필수 값으로 생성합니다. 등록 시각은 지금으로 채웁니다. */
    @Builder
    private FcmToken(User user, String token, FcmPlatform platform) {
        this.user = user;
        this.token = token;
        this.platform = platform;
        this.lastUsedAt = LocalDateTime.now();
    }

    /**
     * 같은 토큰이 다시 등록되면 현재 사용자로 옮기고 등록 시각을 갱신합니다.
     * (같은 기기에서 다른 계정으로 로그인한 경우)
     *
     * @param user 현재 사용자
     * @param platform 기기 종류
     */
    public void refresh(User user, FcmPlatform platform) {
        this.user = user;
        this.platform = platform;
        this.lastUsedAt = LocalDateTime.now();
    }
}
