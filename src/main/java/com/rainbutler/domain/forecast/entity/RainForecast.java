package com.rainbutler.domain.forecast.entity;

import com.rainbutler.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

/**
 * 비 예보입니다. (rain_forecast 테이블)
 */
@Entity
@Table(name = "rain_forecast")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RainForecast extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 비 오는 날 */
    @Column(nullable = false)
    private LocalDate rainDate;

    @Column(nullable = false)
    private LocalDateTime rainStartAt;

    /** 강수확률 최댓값 (%) */
    @Column(nullable = false)
    private Integer pop;

    /** 강수량 합계 (mm) */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal pcpMm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ForecastStatus status;

    /** 시연용 가상 예보 */
    @Column(nullable = false)
    private boolean isSimulated;

    /** 마지막으로 기상청 값을 반영한 시각 */
    private LocalDateTime fetchedAt;

    /** 필수 값으로 생성합니다. 초기 상태·기본값은 여기서 채웁니다. */
    @Builder
    private RainForecast(
            LocalDate rainDate,
            LocalDateTime rainStartAt,
            Integer pop,
            BigDecimal pcpMm,
            boolean isSimulated,
            LocalDateTime fetchedAt) {
        this.rainDate = rainDate;
        this.rainStartAt = rainStartAt;
        this.pop = pop;
        this.pcpMm = pcpMm;
        this.isSimulated = isSimulated;
        this.fetchedAt = fetchedAt;
        this.status = ForecastStatus.ACTIVE;
    }

    /**
     * 기준을 계속 넘는 예보의 값을 새로 받은 값으로 바꿉니다.
     *
     * @param pop 강수확률 최댓값 (%)
     * @param pcpMm 강수량 합계 (mm)
     * @param rainStartAt 비 시작 시각
     * @param fetchedAt 반영 시각 (가상 예보는 null)
     */
    public void refresh(Integer pop, BigDecimal pcpMm, LocalDateTime rainStartAt,
            LocalDateTime fetchedAt) {
        this.pop = pop;
        this.pcpMm = pcpMm;
        this.rainStartAt = rainStartAt;
        this.fetchedAt = fetchedAt;
    }

    /**
     * 기준 미달로 바뀐 예보를 취소합니다. 취소된 예보는 되살리지 않고, 다시 기준을 넘으면 새 예보를 만듭니다.
     */
    public void cancel() {
        this.status = ForecastStatus.CANCELLED;
    }

    /**
     * 진행 중인 예보인지 확인합니다.
     *
     * @return ACTIVE면 true
     */
    public boolean isActive() {
        return status == ForecastStatus.ACTIVE;
    }
}
