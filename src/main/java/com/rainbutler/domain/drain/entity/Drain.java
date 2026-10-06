package com.rainbutler.domain.drain.entity;

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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

/**
 * 빗물받이입니다. (drain 테이블)
 */
@Entity
@Table(name = "drain")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Drain extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 등록자 (현장 조사한 관리자 or 제보자) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registered_by")
    private User registeredBy;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    /** 최신 현장 사진 (청소·관리 때마다 갱신) */
    @Column(nullable = false)
    private String photoUrl;

    /** 최신 더러움 단계 1 / 2 / 3 */
    @Column(nullable = false)
    private Integer dirtLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DrainAdoptionStatus adoptionStatus;

    /** 현장 조사 등록은 ACTIVE, 신규 제보는 PENDING */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DrainStatus status;

    /** 위험 빗물받이 - 현장 조사 때 지정 */
    @Column(nullable = false)
    private boolean isRisk;

    /** 필수 값으로 생성합니다. 초기 상태·기본값은 여기서 채웁니다. */
    @Builder
    private Drain(
            User registeredBy,
            Double latitude,
            Double longitude,
            String photoUrl,
            Integer dirtLevel,
            DrainStatus status,
            boolean isRisk) {
        this.registeredBy = registeredBy;
        this.latitude = latitude;
        this.longitude = longitude;
        this.photoUrl = photoUrl;
        this.dirtLevel = dirtLevel;
        this.status = status;
        this.isRisk = isRisk;
        this.adoptionStatus = DrainAdoptionStatus.AVAILABLE;
    }
}
