package com.rainbutler.domain.bugreport.entity;

import com.rainbutler.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

/**
 * 앱 버그 신고입니다. (bug_report 테이블, 빗물받이 제보와는 별개)
 *
 * <p>신고자 · 처리자는 연관관계가 아니라 사용자 id로 둡니다. 탈퇴한 사용자는 {@code User}의
 * {@code @SQLRestriction}으로 조회되지 않아 연관관계로 불러오면 오류가 나기 때문입니다. 탈퇴해도 신고는 남깁니다.
 */
@Entity
@Table(name = "bug_report")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BugReport extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 신고자 id */
    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BugReportCategory category;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 2000)
    private String content;

    /** 버그가 난 화면 · 경로 */
    @Column(length = 200)
    private String screen;

    /** 프론트가 마지막으로 받은 X-Request-Id */
    @Column(length = 64)
    private String requestId;

    @Column(length = 50)
    private String appVersion;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private BugReportPlatform platform;

    @Column(length = 500)
    private String userAgent;

    /** 스크린샷 S3 key (최대 3개) */
    @Convert(converter = PhotoKeysConverter.class)
    @Column(length = 1000)
    private List<String> photoKeys;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BugReportStatus status;

    /** 관리자 내부 메모 (신고자에게 보여주지 않음) */
    @Column(length = 1000)
    private String adminNote;

    /** 해결 · 보류로 바꾼 관리자 id */
    private Long resolvedBy;

    private LocalDateTime resolvedAt;

    /** 필수 값으로 생성합니다. 상태는 접수(RECEIVED)로 시작합니다. */
    @Builder
    private BugReport(Long userId, BugReportCategory category, String title, String content,
            String screen, String requestId, String appVersion, BugReportPlatform platform,
            String userAgent, List<String> photoKeys) {
        this.userId = userId;
        this.category = category;
        this.title = title;
        this.content = content;
        this.screen = screen;
        this.requestId = requestId;
        this.appVersion = appVersion;
        this.platform = platform;
        this.userAgent = userAgent;
        this.photoKeys = photoKeys == null ? List.of() : List.copyOf(photoKeys);
        this.status = BugReportStatus.RECEIVED;
    }

    /**
     * 관리자가 처리 상태 · 메모를 바꿉니다. null인 값은 그대로 둡니다.
     *
     * <p>해결 · 보류로 바꾸면 처리자 · 처리 시각을 남기고, 접수 · 확인 중으로 되돌리면 둘 다 비웁니다.
     *
     * @param status 새 상태 (null이면 유지)
     * @param adminNote 관리자 메모 (null이면 유지)
     * @param adminId 바꾸는 관리자 id
     * @param now 처리 시각
     */
    public void update(BugReportStatus status, String adminNote, Long adminId, LocalDateTime now) {
        if (adminNote != null) {
            this.adminNote = adminNote;
        }
        if (status == null || status == this.status) {
            return;
        }
        this.status = status;
        if (status.isClosed()) {
            this.resolvedBy = adminId;
            this.resolvedAt = now;
        } else {
            this.resolvedBy = null;
            this.resolvedAt = null;
        }
    }
}
