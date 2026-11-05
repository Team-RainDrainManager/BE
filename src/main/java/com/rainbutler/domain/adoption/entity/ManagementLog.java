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

    /** 점검 전 사진 AI 더러움 단계 1~3 */
    private Integer dirtLevel;

    /** 청소 후 사진 AI 더러움 단계 1~3 (살펴보기만 했으면 null) */
    private Integer afterDirtLevel;

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

    /**
     * 제출한 사진 · 촬영 위치 · AI 판정 결과를 기록합니다.
     *
     * @param beforePhotoUrl 점검 전 사진 key
     * @param afterPhotoUrl 청소 후 사진 key (살펴보기만 했으면 null)
     * @param dirtLevel 점검 전 사진 AI 더러움 단계 (1~3)
     * @param afterDirtLevel 청소 후 사진 AI 더러움 단계 (1~3, 살펴보기만 했으면 null)
     * @param aiConfidence 점검 전 사진 AI 확신도
     * @param memo 남긴 메모
     * @param latitude 촬영 위도
     * @param longitude 촬영 경도
     * @param locationMismatch 촬영 위치가 빗물받이와 기준 거리 이상 떨어졌는지
     */
    public void recordPhotos(
            String beforePhotoUrl,
            String afterPhotoUrl,
            Integer dirtLevel,
            Integer afterDirtLevel,
            BigDecimal aiConfidence,
            String memo,
            Double latitude,
            Double longitude,
            boolean locationMismatch) {
        this.beforePhotoUrl = beforePhotoUrl;
        this.afterPhotoUrl = afterPhotoUrl;
        this.dirtLevel = dirtLevel;
        this.afterDirtLevel = afterDirtLevel;
        this.aiConfidence = aiConfidence;
        this.memo = memo;
        this.latitude = latitude;
        this.longitude = longitude;
        this.locationMismatch = locationMismatch;
    }

    /**
     * 관리 인증에 성공했습니다. 관리 시각을 남깁니다.
     *
     * @param managedAt 관리한 시각
     */
    public void markVerified(LocalDateTime managedAt) {
        this.status = ManagementStatus.VERIFIED;
        this.managedAt = managedAt;
    }

    /** 관리 인증에 실패했습니다. (관리 주기는 갱신하지 않는다) */
    public void markFailed() {
        this.status = ManagementStatus.FAILED;
    }

    /** 위치 불일치 · 낮은 AI 확신도라 관리자 검토가 필요합니다. */
    public void requireReview() {
        this.reviewStatus = ReviewStatus.PENDING;
    }

    /**
     * 관리자 검토에서 승인됐습니다.
     *
     * <p>AI 판정이 실패였더라도 관리자가 인정했으므로 인증 성공으로 바꿉니다. 이미 인증에 성공한 기록은
     * 관리 시각을 그대로 둡니다.
     *
     * @param reviewer 검토한 관리자
     * @param reviewedAt 검토 시각
     */
    public void approve(User reviewer, LocalDateTime reviewedAt) {
        this.reviewStatus = ReviewStatus.APPROVED;
        this.reviewedBy = reviewer;
        this.reviewedAt = reviewedAt;
        if (this.status != ManagementStatus.VERIFIED) {
            markVerified(reviewedAt);
        }
    }

    /**
     * 관리자 검토에서 반려됐습니다. (인정 집계에서 빠진다)
     *
     * @param reviewer 검토한 관리자
     * @param reviewedAt 검토 시각
     */
    public void reject(User reviewer, LocalDateTime reviewedAt) {
        this.reviewStatus = ReviewStatus.REJECTED;
        this.reviewedBy = reviewer;
        this.reviewedAt = reviewedAt;
    }
}
