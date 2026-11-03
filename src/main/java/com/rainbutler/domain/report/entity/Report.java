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
    @JoinColumn(name = "drain_id", nullable = false)
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

    private String adminComment;

    private LocalDateTime resolvedAt;

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
            Double newLongitude) {
        this.user = user;
        this.drain = drain;
        this.type = type;
        this.description = description;
        this.photoUrl = photoUrl;
        this.latitude = latitude;
        this.longitude = longitude;
        this.newLatitude = newLatitude;
        this.newLongitude = newLongitude;
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
}
