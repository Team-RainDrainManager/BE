package com.rainbutler.domain.photo.entity;

import com.rainbutler.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

/**
 * 사진 해시 (같은 사진 재사용 차단)입니다. (photo_hash 테이블)
 */
@Entity
@Table(name = "photo_hash")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PhotoHash extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** SHA-256 */
    @Column(nullable = false, unique = true, length = 64)
    private String hash;

    /** 사진이 쓰인 테이블 (cleaning / management_log / ai_verification / report / drain) */
    @Column(nullable = false)
    private String sourceTable;

    /** 사진이 쓰인 행의 id */
    @Column(nullable = false)
    private Long sourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PhotoRole photoRole;

    /** 필수 값으로 생성합니다. */
    @Builder
    private PhotoHash(String hash, String sourceTable, Long sourceId, PhotoRole photoRole) {
        this.hash = hash;
        this.sourceTable = sourceTable;
        this.sourceId = sourceId;
        this.photoRole = photoRole;
    }
}
