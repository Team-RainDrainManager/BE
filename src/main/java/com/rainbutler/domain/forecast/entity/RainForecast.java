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

    /** 강수확률 (%) */
    @Column(nullable = false)
    private Integer pop;

    /** 1시간 강수량 (mm) */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal pcpMm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ForecastStatus status;

    /** 시연용 가상 예보 */
    @Column(nullable = false)
    private boolean isSimulated;

    /** 필수 값으로 생성합니다. 초기 상태·기본값은 여기서 채웁니다. */
    @Builder
    private RainForecast(
            LocalDate rainDate,
            LocalDateTime rainStartAt,
            Integer pop,
            BigDecimal pcpMm,
            boolean isSimulated) {
        this.rainDate = rainDate;
        this.rainStartAt = rainStartAt;
        this.pop = pop;
        this.pcpMm = pcpMm;
        this.isSimulated = isSimulated;
        this.status = ForecastStatus.ACTIVE;
    }
}
