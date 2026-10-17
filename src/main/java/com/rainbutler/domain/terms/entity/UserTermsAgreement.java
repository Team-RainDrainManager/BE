package com.rainbutler.domain.terms.entity;

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
 * 사용자의 약관 동의 이력입니다. (user_terms_agreement 테이블)
 *
 * <p>동의 · 철회할 때마다 새 행을 추가하고, 기존 행은 수정하지 않습니다. 선택 약관에 동의하지 않은 것도
 * {@code agreed = false}로 남깁니다.
 */
@Entity
@Table(name = "user_terms_agreement")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserTermsAgreement extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "terms_id", nullable = false)
    private Terms terms;

    /** 동의 여부 */
    @Column(nullable = false)
    private boolean agreed;

    /** 동의(또는 철회) 시각 */
    @Column(nullable = false)
    private LocalDateTime agreedAt;

    /** 필수 값으로 생성합니다. */
    @Builder
    private UserTermsAgreement(User user, Terms terms, boolean agreed, LocalDateTime agreedAt) {
        this.user = user;
        this.terms = terms;
        this.agreed = agreed;
        this.agreedAt = agreedAt;
    }
}
