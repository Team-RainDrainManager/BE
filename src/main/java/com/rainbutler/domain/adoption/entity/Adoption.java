package com.rainbutler.domain.adoption.entity;

import com.rainbutler.domain.cleaning.entity.Cleaning;
import com.rainbutler.domain.drain.entity.Drain;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

/**
 * 빗물받이 입양입니다. (adoption 테이블)
 */
@Entity
@Table(name = "adoption")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Adoption extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "drain_id", nullable = false)
    private Drain drain;

    /** 입양 조건 청소 (청소 1건 = 입양 1건) */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cleaning_id", unique = true, nullable = false)
    private Cleaning cleaning;

    /** 빗물받이에 붙인 이름 */
    @Column(nullable = false)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AdoptionStatus status;

    @Enumerated(EnumType.STRING)
    private ReleaseReason releaseReason;

    /** 빗물받이 캐릭터 레벨 - 입양자 바뀌면 초기화 */
    @Column(nullable = false)
    private Integer level;

    @Column(nullable = false)
    private Integer score;

    /** 비 예보 요청 연속 무응답 횟수 */
    @Column(nullable = false)
    private Integer ignoreCount;

    @Column(nullable = false)
    private LocalDateTime adoptedAt;

    /** 마지막 점검일 - 35일 미점검 자동 파양 판정 */
    private LocalDateTime lastManagedAt;

    /** 다음 관리 예정일 */
    private LocalDateTime nextManagementAt;

    private LocalDateTime releasedAt;

    /** 필수 값으로 생성합니다. 초기 상태·기본값은 여기서 채웁니다. */
    @Builder
    private Adoption(
            User user,
            Drain drain,
            Cleaning cleaning,
            String nickname,
            LocalDateTime adoptedAt) {
        this.user = user;
        this.drain = drain;
        this.cleaning = cleaning;
        this.nickname = nickname;
        this.adoptedAt = adoptedAt;
        this.status = AdoptionStatus.ACTIVE;
        this.level = 1;
        this.score = 0;
        this.ignoreCount = 0;
    }
}
