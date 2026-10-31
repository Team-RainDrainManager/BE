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

    /**
     * 다음 관리 예정일을 미룹니다. 이미 더 늦으면 그대로 둡니다. (점검 쉬기)
     *
     * @param until 이 시각 이후로 미룬다
     */
    public void postponeNextManagement(LocalDateTime until) {
        if (this.nextManagementAt == null || this.nextManagementAt.isBefore(until)) {
            this.nextManagementAt = until;
        }
    }

    /**
     * 입양을 해제합니다. 빗물받이는 다시 입양할 수 있게 됩니다. (직접 해제 · 자동 해제 · 회원 탈퇴 공통)
     *
     * <p>이 입양에 걸린 점검 요청 재공개는 호출하는 쪽에서 {@code InspectionRequest.reopen}으로 처리합니다.
     *
     * @param reason 해제 사유
     * @param now 해제 시각
     */
    public void release(ReleaseReason reason, LocalDateTime now) {
        this.status = AdoptionStatus.RELEASED;
        this.releaseReason = reason;
        this.releasedAt = now;
        this.drain.markAvailable();
    }

    /** 마지막 점검일 설정 */
    public void setLastManagedAt(LocalDateTime lastManagedAt) {
        this.lastManagedAt = lastManagedAt;
    }

    /** 다음 관리 예정일 설정 */
    public void setNextManagementAt(LocalDateTime nextManagementAt) {
        this.nextManagementAt = nextManagementAt;
    }

    /**
     * 비 예보 요청에 응답했으므로 연속 무응답 횟수를 초기화합니다.
     */
    public void resetIgnoreCount() {
        this.ignoreCount = 0;
    }

    /**
     * 비 예보 요청에 응답 마감까지 응답하지 않아 연속 무응답 횟수를 늘립니다.
     */
    public void increaseIgnoreCount() {
        this.ignoreCount = this.ignoreCount + 1;
    }

    /**
     * "외로운 또랑이"(연속 무응답이 기준 이상)인지 확인합니다. 표시용이고 불이익은 없습니다.
     *
     * @param threshold 기준 횟수 ({@code rain-butler.policy.lonely-ignore-count})
     * @return 기준 이상이면 true
     */
    public boolean isLonely(int threshold) {
        return this.ignoreCount >= threshold;
    }
}
