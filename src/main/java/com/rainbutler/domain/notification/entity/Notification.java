package com.rainbutler.domain.notification.entity;

import com.rainbutler.domain.adoption.entity.Adoption;
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
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

/**
 * 사용자 알림입니다. (notification 테이블)
 */
@Entity
@Table(name = "notification")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id")
    private InspectionRequest request;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "adoption_id")
    private Adoption adoption;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String body;

    private LocalDateTime sentAt;

    private LocalDateTime readAt;

    /** 필수 값으로 생성합니다. */
    @Builder
    private Notification(
            User user,
            InspectionRequest request,
            Adoption adoption,
            NotificationType type,
            String title,
            String body) {
        this.user = user;
        this.request = request;
        this.adoption = adoption;
        this.type = type;
        this.title = title;
        this.body = body;
    }
}
