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

    /** 발송 시각 (기본 06:00, 당일 예보는 즉시) */
    @Column(nullable = false)
    private LocalDateTime sentAt;

    /** 응답 마감 (기본 발송 당일 12:00, 당일 예보는 발송 + 1시간) - 지나면 대신 점검으로 공개 */
    @Column(nullable = false)
    private LocalDateTime replyDeadlineAt;

    /** 완료 마감 (기본 20:00, 당일 예보는 비 시작 1시간 전) - 지나면 MISSED */
    @Column(nullable = false)
    private LocalDateTime completeDeadlineAt;

    private LocalDateTime repliedAt;

    private LocalDateTime openedAt;

    private LocalDateTime claimedAt;

    private LocalDateTime completedAt;

    private LocalDateTime cancelledAt;

    /** 완료 마감까지 미완료로 시청 인계된 시각 */
    private LocalDateTime missedAt;

    /** 필수 값으로 생성합니다. */
    @Builder
    private InspectionRequest(
            RainForecast forecast,
            Drain drain,
            Adoption adoption,
            RequestStatus status,
            LocalDateTime sentAt,
            LocalDateTime replyDeadlineAt,
            LocalDateTime completeDeadlineAt) {
        this.forecast = forecast;
        this.drain = drain;
        this.adoption = adoption;
        this.status = status;
        this.sentAt = sentAt;
        this.replyDeadlineAt = replyDeadlineAt;
        this.completeDeadlineAt = completeDeadlineAt;
        if (status == RequestStatus.OPEN) {
            this.openedAt = sentAt;
        }
    }

    /**
     * 요청을 대신 점검으로 다시 공개합니다. (입양 해제 · 대신 점검자 탈퇴)
     *
     * <p>입양자에게 간 요청(SENT · ACCEPTED)이나 누군가 맡았던 요청(CLAIMED)을 OPEN으로 돌리고, 맡았던 사람을 비웁니다.
     *
     * @param now 공개 시각
     */
    public void reopen(LocalDateTime now) {
        this.status = RequestStatus.OPEN;
        this.openedAt = now;
        this.substituteUser = null;
        this.claimedAt = null;
    }
}
