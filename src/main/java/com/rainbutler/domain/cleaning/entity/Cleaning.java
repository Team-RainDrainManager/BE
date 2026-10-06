package com.rainbutler.domain.cleaning.entity;

import com.rainbutler.domain.drain.entity.Drain;
import com.rainbutler.domain.request.entity.InspectionRequest;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.global.entity.BaseEntity;
import com.rainbutler.global.entity.ReviewStatus;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

/**
 * 청소 (입양 전 청소 / 대신 점검)입니다. (cleaning 테이블)
 */
@Entity
@Table(name = "cleaning")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cleaning extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "drain_id", nullable = false)
    private Drain drain;

    /** 대신 점검이면 연결 */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", unique = true)
    private InspectionRequest request;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CleaningType type;

    @Column(nullable = false)
    private String beforePhotoUrl;

    /** AI가 비포 사진으로 판정한 더러움 1~3 */
    private Integer dirtLevel;

    @Column(precision = 3, scale = 2)
    private BigDecimal dirtConfidence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CleaningStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewStatus reviewStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    private LocalDateTime reviewedAt;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    /** 촬영 위치가 50m 이상 떨어졌는지 */
    @Column(nullable = false)
    private boolean locationMismatch;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    /** 필수 값으로 생성합니다. 초기 상태·기본값은 여기서 채웁니다. */
    @Builder
    private Cleaning(
            User user,
            Drain drain,
            InspectionRequest request,
            CleaningType type,
            String beforePhotoUrl,
            Double latitude,
            Double longitude,
            LocalDateTime startedAt) {
        this.user = user;
        this.drain = drain;
        this.request = request;
        this.type = type;
        this.beforePhotoUrl = beforePhotoUrl;
        this.latitude = latitude;
        this.longitude = longitude;
        this.startedAt = startedAt;
        this.status = CleaningStatus.IN_PROGRESS;
        this.reviewStatus = ReviewStatus.NOT_REQUIRED;
    }
}
