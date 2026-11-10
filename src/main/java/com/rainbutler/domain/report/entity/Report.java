package com.rainbutler.domain.report.entity;

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
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

/**
 * 사용자 제보입니다. (report 테이블)
 */
@Entity
@Table(name = "report")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "drain_id")
    private Drain drain;

    /** 처리한 시청 관계자·관리자 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by")
    private User processedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportType type;

    private String description;

    private String photoUrl;

    /** 촬영 위치 */
    private Double latitude;

    private Double longitude;

    /** LOCATION_FIX 제보의 새 위치 */
    private Double newLatitude;

    private Double newLongitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportStatus status;

    /**
     * 이 제보가 빗물받이 입양을 막는지 여부입니다.
     *
     * <p>AI가 청소할 수 없는 상태(막힘(3등급) 또는 {@code needsReport=true})로 판정한 막힘 제보만
     * true입니다.
     */
    @Column(nullable = false)
    private boolean blocksAdoption;

    private String adminComment;

    private LocalDateTime resolvedAt;

    /** 승인된 제보의 입양 차단을 해제한 시각 (해제 전에는 null) */
    private LocalDateTime clearedAt;

    /** 필수 값으로 생성합니다. 초기 상태·기본값은 여기서 채웁니다. */
    @Builder
    private Report(
            User user,
            Drain drain,
            ReportType type,
            String description,
            String photoUrl,
            Double latitude,
            Double longitude,
            Double newLatitude,
            Double newLongitude,
            boolean blocksAdoption) {
        this.user = user;
        this.drain = drain;
        this.type = type;
        this.description = description;
        this.photoUrl = photoUrl;
        this.latitude = latitude;
        this.longitude = longitude;
        this.newLatitude = newLatitude;
        this.newLongitude = newLongitude;
        this.blocksAdoption = blocksAdoption;
        this.status = ReportStatus.PENDING;
    }

    /** 아직 관리자가 처리하지 않은 제보인지 확인합니다. */
    public boolean isPending() {
        return this.status == ReportStatus.PENDING;
    }

    /**
     * 관리자 검토에서 승인합니다.
     *
     * @param admin 처리한 관리자 · 시청 관계자
     * @param comment 관리자 코멘트 (없으면 null)
     * @param now 처리 시각
     */
    public void approve(User admin, String comment, LocalDateTime now) {
        this.status = ReportStatus.APPROVED;
        this.processedBy = admin;
        this.adminComment = comment;
        this.resolvedAt = now;
    }

    /**
     * 관리자 검토에서 반려합니다.
     *
     * @param admin 처리한 관리자 · 시청 관계자
     * @param comment 반려 사유
     * @param now 처리 시각
     */
    public void reject(User admin, String comment, LocalDateTime now) {
        this.status = ReportStatus.REJECTED;
        this.processedBy = admin;
        this.adminComment = comment;
        this.resolvedAt = now;
    }

    /**
     * 승인된 제보의 입양 차단을 해제합니다.
     *
     * <p>승인 이후 지자체가 막힘을 처리했음을 관리자가 확인했을 때 씁니다. 반려는
     * {@link #reject}가 상태를 {@code REJECTED}로 바꾸므로 자동으로 차단이 풀립니다.
     *
     * @param admin 처리한 관리자 · 시청 관계자
     * @param comment 해제 코멘트 (없으면 기존 코멘트 유지)
     * @param now 해제 시각
     */
    public void clear(User admin, String comment, LocalDateTime now) {
        this.status = ReportStatus.CLEARED;
        this.processedBy = admin;
        if (comment != null) {
            this.adminComment = comment;
        }
        this.clearedAt = now;
    }
}
