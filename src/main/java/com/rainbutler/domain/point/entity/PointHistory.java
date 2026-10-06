package com.rainbutler.domain.point.entity;

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
 * 포인트 적립·회수 이력입니다. (point_history 테이블)
 */
@Entity
@Table(name = "point_history")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PointSourceType sourceType;

    /** 출처 행의 id */
    @Column(nullable = false)
    private Long sourceId;

    @Column(nullable = false)
    private Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PointType type;

    private String description;

    /** 필수 값으로 생성합니다. */
    @Builder
    private PointHistory(
            User user,
            PointSourceType sourceType,
            Long sourceId,
            Integer amount,
            PointType type,
            String description) {
        this.user = user;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.amount = amount;
        this.type = type;
        this.description = description;
    }
}
