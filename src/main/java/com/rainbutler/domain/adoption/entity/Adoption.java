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

    /** 같은 관리 주기에서 미관리 경고를 보낸 시각 */
    private LocalDateTime warnedAt;

    /** 이번 관리 주기에서 관리 예정일 하루 전 알림을 보낸 시각 */
    private LocalDateTime managementNotifiedAt;

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
     * <p>예정일이 실제로 밀리면 이전 예정일에 보낸 사전 알림은 지난 주기의 것이 되므로 다시 알릴 수 있게 합니다.
     *
     * @param until 이 시각 이후로 미룬다
     */
    public void postponeNextManagement(LocalDateTime until) {
        if (this.nextManagementAt == null || this.nextManagementAt.isBefore(until)) {
            this.nextManagementAt = until;
            this.managementNotifiedAt = null;
        }
    }

    /**
     * 입양을 해제합니다. 빗물받이는 다시 입양할 수 있게 됩니다. (직접 해제 · 자동 해제 · 회원 탈퇴 공통)
     *
     * <p>이 입양에 걸린 점검 요청 재공개는 호출하는 쪽에서 {@code InspectionRequest.reopenAfterRelease}로 처리합니다.
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

    /**
     * 관리(점검 · 청소)를 마친 것으로 기록하고 다음 관리 예정일을 다시 잡습니다.
     *
     * <p>입양 시점과 관리 기록 저장 시점이 같이 씁니다. 관리가 새로 생기면 미관리 경고 · 관리 예정일 알림도
     * 새 주기로 다시 보낼 수 있습니다.
     *
     * @param managedAt 관리한 시각
     * @param nextManagementAt 다음 관리 예정일
     */
    public void recordManagement(LocalDateTime managedAt, LocalDateTime nextManagementAt) {
        this.lastManagedAt = managedAt;
        this.nextManagementAt = nextManagementAt;
        this.managementNotifiedAt = null;
    }

    /**
     * 이번 관리 주기의 관리 예정일 하루 전 알림을 보냈음을 기록합니다.
     *
     * @param now 알림 발송 시각
     */
    public void markManagementNotified(LocalDateTime now) {
        this.managementNotifiedAt = now;
    }

    /**
     * 이번 관리 주기의 관리 예정일 하루 전 알림을 이미 보냈는지 확인합니다.
     *
     * @return 이미 보냈으면 true
     */
    public boolean isManagementNotified() {
        return this.managementNotifiedAt != null;
    }

    /**
     * 미관리 경고를 보냈음을 기록합니다.
     *
     * @param now 경고 발송 시각
     */
    public void markWarned(LocalDateTime now) {
        this.warnedAt = now;
    }

    /**
     * 이 관리 주기에서 미관리 경고를 이미 보냈는지 확인합니다.
     *
     * <p>관리(점검) 기록이 생겨 기준 시각이 뒤로 밀리면 이전 경고는 지난 주기의 것이 되어 다시 경고할 수 있습니다.
     *
     * @param baseline 관리 기준 시각 (입양일 · 마지막 관리일 · 지난 쉬기 종료일 중 가장 늦은 시각)
     * @return 이미 경고했으면 true
     */
    public boolean isWarningSentSince(LocalDateTime baseline) {
        return this.warnedAt != null && !this.warnedAt.isBefore(baseline);
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
