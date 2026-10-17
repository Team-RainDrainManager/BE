package com.rainbutler.domain.terms.entity;

import com.rainbutler.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

/**
 * 약관입니다. (terms 테이블)
 *
 * <p>개정하면 같은 type에 version을 올린 새 행을 추가합니다. 현재 약관은 type별로 시행 중인 최신 버전입니다.
 * 행은 Flyway 시드로 만들고, 코드에서는 읽기만 합니다.
 */
@Entity
@Table(name = "terms")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Terms extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TermsType type;

    /** 약관 버전 (type별 1부터) */
    @Column(nullable = false)
    private Integer version;

    /** 체크박스 문구 ([필수]/[선택] 접두어 제외) */
    @Column(nullable = false)
    private String title;

    /** 필수 동의 여부 */
    @Column(nullable = false)
    private boolean required;

    /** 약관 전문 markdown의 classpath 경로 (전문 없으면 null) */
    private String contentPath;

    /** 화면 표시 순서 */
    @Column(nullable = false)
    private Integer displayOrder;

    /** 시행 시각 */
    @Column(nullable = false)
    private LocalDateTime effectiveAt;

    /**
     * 전문이 있는지 확인합니다.
     *
     * @return 전문 존재 여부
     */
    public boolean hasContent() {
        return contentPath != null && !contentPath.isBlank();
    }
}
