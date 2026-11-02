package com.rainbutler.domain.inspection.entity;

import com.rainbutler.domain.adoption.entity.Adoption;
import com.rainbutler.domain.drain.entity.Drain;
import com.rainbutler.domain.request.entity.InspectionRequest;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

/**
 * 점검 · 청소 기록입니다. (inspection 테이블)
 *
 * <p>정기 점검 · 비 예보 점검 · 대신 점검을 한 테이블에 모읍니다. 점검 전 사진으로 한 번 만들고, 청소까지
 * 했으면 점검 후 사진을 같은 기록에 붙입니다.
 */
@Entity
@Table(name = "inspection")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inspection extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 점검한 사용자 (입양자 또는 대신 점검자) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "drain_id", nullable = false)
    private Drain drain;

    /** 점검 대상 입양. 입양되지 않은 빗물받이의 대신 점검이면 null */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "adoption_id")
    private Adoption adoption;

    /** 비 예보 · 대신 점검 요청. 정기 점검이면 null */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", unique = true)
    private InspectionRequest request;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InspectionKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InspectionStatus status;

    @Column(nullable = false)
    private String beforePhotoKey;

    private String afterPhotoKey;

    /** AI가 점검 전 사진으로 판정한 등급 (0~3) */
    private Integer beforeGrade;

    /** AI가 점검 후 사진으로 판정한 등급 (0~3) */
    private Integer afterGrade;

    /** 점검 전 사진 AI 확신도 */
    @Column(precision = 3, scale = 2)
    private BigDecimal aiConfidence;

    /** 점검 후 사진 AI 확신도 */
    @Column(precision = 3, scale = 2)
    private BigDecimal afterConfidence;

    /** 촬영 위치와 빗물받이 사이 거리 (m) */
    @Column(nullable = false)
    private Integer distanceM;

    /** 사진 촬영 시각 */
    @Column(nullable = false)
    private LocalDateTime capturedAt;

    private String note;

    /** 이 점검으로 지급한 경험치 (검토 대기 · 청소 필요면 0) */
    @Column(nullable = false)
    private Integer xpAwarded;

    /** 경험치 지급 처리를 마쳤는지 (중복 지급 방지) */
    @Column(nullable = false)
    private boolean rewarded;

    /** 필수 값으로 생성합니다. 점검 전 사진을 낸 직후 상태는 {@code NEEDS_CLEANING}입니다. */
    @Builder
    private Inspection(
            User user,
            Drain drain,
            Adoption adoption,
            InspectionRequest request,
            InspectionKind kind,
            String beforePhotoKey,
            Integer beforeGrade,
            BigDecimal aiConfidence,
            Integer distanceM,
            LocalDateTime capturedAt,
            String note) {
        this.user = user;
        this.drain = drain;
        this.adoption = adoption;
        this.request = request;
        this.kind = kind;
        this.beforePhotoKey = beforePhotoKey;
        this.beforeGrade = beforeGrade;
        this.aiConfidence = aiConfidence;
        this.distanceM = distanceM;
        this.capturedAt = capturedAt;
        this.note = note;
        this.status = InspectionStatus.NEEDS_CLEANING;
        this.xpAwarded = 0;
        this.rewarded = false;
    }

    /** 점검 전 사진만으로 깨끗함이 확인돼 청소 없이 점검을 마칩니다. */
    public void completeWithoutCleaning() {
        this.status = InspectionStatus.COMPLETED;
    }

    /** 위치 불일치 · 낮은 AI 확신도라 관리자 검토가 필요합니다. (점검 전 사진 단계) */
    public void requireReview() {
        this.status = InspectionStatus.PENDING;
    }

    /**
     * 청소 후 사진을 붙이지만 아직 인증을 통과하지 못했습니다. (다시 청소하고 재시도)
     *
     * @param afterPhotoKey 청소 후 사진 key
     * @param afterGrade AI가 판정한 청소 후 등급 (0~3)
     * @param afterConfidence AI 확신도
     */
    public void retryCleaning(String afterPhotoKey, int afterGrade, double afterConfidence) {
        recordAfter(afterPhotoKey, afterGrade, afterConfidence);
        this.status = InspectionStatus.NEEDS_CLEANING;
    }

    /**
     * 청소 후 등급이 좋아져 청소 인증을 통과했습니다.
     *
     * @param afterPhotoKey 청소 후 사진 key
     * @param afterGrade AI가 판정한 청소 후 등급 (0~3)
     * @param afterConfidence AI 확신도
     */
    public void completeCleaning(String afterPhotoKey, int afterGrade, double afterConfidence) {
        recordAfter(afterPhotoKey, afterGrade, afterConfidence);
        this.status = InspectionStatus.COMPLETED;
    }

    /**
     * 청소 후 등급은 좋아졌지만 위치 불일치 · 낮은 확신도라 관리자 검토가 필요합니다.
     *
     * @param afterPhotoKey 청소 후 사진 key
     * @param afterGrade AI가 판정한 청소 후 등급 (0~3)
     * @param afterConfidence AI 확신도
     */
    public void requireReviewAfterCleaning(
            String afterPhotoKey, int afterGrade, double afterConfidence) {
        recordAfter(afterPhotoKey, afterGrade, afterConfidence);
        this.status = InspectionStatus.PENDING;
    }

    /**
     * 경험치 지급 결과를 남깁니다. 금액이 0이어도 처리 완료로 표시해 중복 지급을 막습니다.
     *
     * @param xp 지급한 경험치
     */
    public void reward(int xp) {
        this.xpAwarded = xp;
        this.rewarded = true;
    }

    /** 관리자 검토에서 승인됐습니다. (검토 대기 → 완료) */
    public void approve() {
        this.status = InspectionStatus.COMPLETED;
    }

    /** 관리자 검토에서 반려됐습니다. (검토 대기 → 반려) */
    public void reject() {
        this.status = InspectionStatus.REJECTED;
    }

    private void recordAfter(String afterPhotoKey, int afterGrade, double afterConfidence) {
        this.afterPhotoKey = afterPhotoKey;
        this.afterGrade = afterGrade;
        this.afterConfidence = BigDecimal.valueOf(afterConfidence);
    }
}
