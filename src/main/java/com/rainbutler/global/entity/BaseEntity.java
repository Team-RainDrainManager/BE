package com.rainbutler.global.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 모든 엔티티 공통 시각 컬럼.
 * - created_at, updated_at: JPA Auditing이 자동으로 채운다.
 * - deleted_at: 소프트 삭제. null이면 살아 있는 행.
 *   조회에서 제외하려면 엔티티에 @SQLRestriction("deleted_at IS NULL")을 붙인다.
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public void softDelete() {
        if (this.deletedAt == null) {
            this.deletedAt = LocalDateTime.now();
        }
    }

    public void restore() {
        this.deletedAt = null;
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}
