package com.rainbutler.domain.cleaning.entity;

import com.rainbutler.domain.adoption.entity.ManagementLog;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

/**
 * 애프터 사진 AI 인증 기록. cleaning / managementLog 중 하나에만 붙습니다 (DB CHECK 제약)입니다. (ai_verification 테이블)
 */
@Entity
@Table(name = "ai_verification")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiVerification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cleaning_id")
    private Cleaning cleaning;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "management_log_id")
    private ManagementLog managementLog;

    /** 시도 회차 */
    @Column(nullable = false)
    private Integer attemptNo;

    @Column(nullable = false)
    private String afterPhotoUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationResult result;

    @Column(precision = 3, scale = 2)
    private BigDecimal confidence;

    /** AI 판정 사유 */
    private String reason;

    @Column(nullable = false)
    private LocalDateTime verifiedAt;

    /** 필수 값으로 생성합니다. */
    @Builder
    private AiVerification(
            Cleaning cleaning,
            ManagementLog managementLog,
            Integer attemptNo,
            String afterPhotoUrl,
            VerificationResult result,
            BigDecimal confidence,
            String reason,
            LocalDateTime verifiedAt) {
        this.cleaning = cleaning;
        this.managementLog = managementLog;
        this.attemptNo = attemptNo;
        this.afterPhotoUrl = afterPhotoUrl;
        this.result = result;
        this.confidence = confidence;
        this.reason = reason;
        this.verifiedAt = verifiedAt;
    }
}
