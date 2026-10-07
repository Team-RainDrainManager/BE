package com.rainbutler.domain.request.entity;

import com.rainbutler.domain.adoption.entity.Adoption;
import com.rainbutler.domain.drain.entity.Drain;
import com.rainbutler.domain.forecast.entity.RainForecast;
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
 * 비 예보에 따른 빗물받이 점검 요청입니다. (inspection_request 테이블)
 */
@Entity
@Table(name = "inspection_request")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InspectionRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "forecast_id", nullable = false)
    private RainForecast forecast;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "drain_id", nullable = false)
    private Drain drain;

    /** 입양자에게 간 요청. 미입양 위험 빗물받이면 null → 바로 대신 점검 공개 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "adoption_id")
    private Adoption adoption;

    /** 대신 점검 수락자 (선착순 1명) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "substitute_user_id")
    private User substituteUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status;

    /** null이면 무응답 */
    @Enumerated(EnumType.STRING)
    private RequestReply reply;

    /** 전날 06:00 일괄 발송 */
    @Column(nullable = false)
    private LocalDateTime sentAt;

    /** 응답 마감 (전날 20:00) - 지나면 대신 점검으로 공개 */
    @Column(nullable = false)
    private LocalDateTime replyDeadlineAt;

    private LocalDateTime repliedAt;

    private LocalDateTime openedAt;

    private LocalDateTime claimedAt;

    private LocalDateTime completedAt;

    /** 필수 값으로 생성합니다. */
    @Builder
    private InspectionRequest(
            RainForecast forecast,
            Drain drain,
            Adoption adoption,
            RequestStatus status,
            LocalDateTime sentAt,
            LocalDateTime replyDeadlineAt) {
        this.forecast = forecast;
        this.drain = drain;
        this.adoption = adoption;
        this.status = status;
        this.sentAt = sentAt;
        this.replyDeadlineAt = replyDeadlineAt;
    }
}
