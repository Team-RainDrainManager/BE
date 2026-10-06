package com.rainbutler.domain.adoption.entity;

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
 * 입양자의 관리(점검) 기록입니다. (management_log 테이블)
 */
@Entity
@Table(name = "management_log")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ManagementLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "adoption_id", nullable = false)
    private Adoption adoption;

    /** 비 예보 요청으로 한 관리면 연결 (type = RAIN일 때만, DB CHECK 제약) */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", unique = true)
    private InspectionRequest request;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ManagementType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ManagementActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ManagementStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewStatus reviewStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    private LocalDateTime reviewedAt;

    private String beforePhotoUrl;

    private String afterPhotoUrl;

    /** AI 더러움 단계 1~3 */
    private Integer dirtLevel;

    @Column(precision = 3, scale = 2)
    private BigDecimal aiConfidence;

    private String memo;

    /** 촬영 위치 */
    private Double latitude;

    private Double longitude;

    /** 촬영 위치가 50m 이상 떨어졌는지 */
    @Column(nullable = false)
    private boolean locationMismatch;

    private LocalDateTime managedAt;

    /** 필수 값으로 생성합니다. 초기 상태·기본값은 여기서 채웁니다. */
    @Builder
    private ManagementLog(
            Adoption adoption,
            InspectionRequest request,
            ManagementType type,
            ManagementActionType actionType) {
        this.adoption = adoption;
        this.request = request;
        this.type = type;
        this.actionType = actionType;
        this.status = ManagementStatus.SUBMITTED;
        this.reviewStatus = ReviewStatus.NOT_REQUIRED;
    }
}
