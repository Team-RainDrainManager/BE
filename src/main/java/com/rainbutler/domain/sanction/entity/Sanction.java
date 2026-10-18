package com.rainbutler.domain.sanction.entity;

import com.rainbutler.domain.report.entity.Report;
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
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

/**
 * 사용자 제재입니다. (sanction 테이블)
 *
 * <p>허위 제보로 1회 7일 · 2회 14일 · 3회 이상 영구 제재합니다. 영구 제재는 종료 시각이 없고, 기간 제재는
 * 종료 시각이 반드시 있습니다. 생성은 {@link #temporary} · {@link #permanent}로만 합니다.
 */
@Entity
@Table(name = "sanction")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Sanction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 원인 제보 (없을 수 있음) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id")
    private Report report;

    /** 부과한 관리자 · 시청 사용자 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "imposed_by")
    private User imposedBy;

    /** 제재 사유 (사용자에게 보임) */
    @Column(nullable = false)
    private String reason;

    /** 시작 시각 */
    @Column(nullable = false)
    private LocalDateTime startsAt;

    /** 종료 시각 (영구면 null) */
    private LocalDateTime until;

    /** 영구 제재 여부 */
    @Column(nullable = false)
    private boolean permanent;

    /** 관리자 해제 시각 */
    private LocalDateTime revokedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private Sanction(User user, Report report, User imposedBy, String reason,
            LocalDateTime startsAt, LocalDateTime until, boolean permanent) {
        this.user = user;
        this.report = report;
        this.imposedBy = imposedBy;
        this.reason = reason;
        this.startsAt = startsAt;
        this.until = until;
        this.permanent = permanent;
    }

    /**
     * 기간 제재를 만듭니다.
     *
     * @param user 제재 대상
     * @param report 원인 제보 (없으면 null)
     * @param imposedBy 부과한 관리자 (없으면 null)
     * @param reason 사유
     * @param startsAt 시작 시각
     * @param until 종료 시각 (시작 시각보다 뒤)
     * @return 기간 제재
     */
    public static Sanction temporary(User user, Report report, User imposedBy, String reason,
            LocalDateTime startsAt, LocalDateTime until) {
        Objects.requireNonNull(until, "기간 제재는 종료 시각이 필요합니다.");
        if (!until.isAfter(startsAt)) {
            throw new IllegalArgumentException("종료 시각은 시작 시각보다 뒤여야 합니다.");
        }
        return Sanction.builder()
                .user(user).report(report).imposedBy(imposedBy).reason(reason)
                .startsAt(startsAt).until(until).permanent(false)
                .build();
    }

    /**
     * 영구 제재를 만듭니다. (종료 시각 없음)
     *
     * @param user 제재 대상
     * @param report 원인 제보 (없으면 null)
     * @param imposedBy 부과한 관리자 (없으면 null)
     * @param reason 사유
     * @param startsAt 시작 시각
     * @return 영구 제재
     */
    public static Sanction permanent(User user, Report report, User imposedBy, String reason,
            LocalDateTime startsAt) {
        return Sanction.builder()
                .user(user).report(report).imposedBy(imposedBy).reason(reason)
                .startsAt(startsAt).until(null).permanent(true)
                .build();
    }

    /**
     * 지금 효력이 있는 제재인지 확인합니다. (해제 안 됨 · 시작됨 · 영구이거나 종료 전)
     *
     * @param now 기준 시각
     * @return 효력 여부
     */
    public boolean isActive(LocalDateTime now) {
        return revokedAt == null
                && !startsAt.isAfter(now)
                && (permanent || until.isAfter(now));
    }

    /**
     * 관리자가 제재를 해제합니다. 해제된 제재는 누적 횟수에서 빠집니다.
     *
     * @param now 해제 시각
     */
    public void revoke(LocalDateTime now) {
        if (this.revokedAt == null) {
            this.revokedAt = now;
        }
    }
}
