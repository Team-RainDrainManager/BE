package com.rainbutler.domain.forecast.service;

import com.rainbutler.domain.forecast.entity.RainForecast;
import com.rainbutler.domain.forecast.event.ForecastCancelledEvent;
import com.rainbutler.domain.forecast.event.ForecastConfirmedEvent;
import com.rainbutler.domain.forecast.event.ForecastRainStartAdvancedEvent;
import com.rainbutler.domain.forecast.repository.RainForecastRepository;
import com.rainbutler.domain.forecast.service.ForecastApplyResult.Applied;
import com.rainbutler.global.config.PolicyProperties;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비 예보를 확정 · 갱신 · 취소합니다. (기상청 수집 배치와 가상 예보 API가 함께 씁니다)
 *
 * <p>기준: 강수확률 최댓값 ≥ {@code rain-pop-threshold} <b>그리고</b> 강수량 합계 ≥ {@code rain-mm-threshold}.
 * 확정 · 비 시작 앞당김 · 취소 때 이벤트를 발행하고, 점검 요청은 이벤트를 받는 쪽이 처리합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForecastService {

    /** 비 시작 시각을 모를 때 쓰는 임시 시각 */
    private static final LocalTime DEFAULT_RAIN_START = LocalTime.of(9, 0);
    /** pcp_mm 컬럼(numeric(5,2))에 들어가는 최댓값 */
    private static final BigDecimal MAX_MM = new BigDecimal("999.99");

    private final RainForecastRepository rainForecastRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PolicyProperties policy;
    private final Clock clock;

    /**
     * 하루치 예보 값을 반영합니다.
     *
     * <ul>
     *   <li>기준 충족 + 진행 중 예보 없음 → 새 예보 확정 ({@link ForecastConfirmedEvent})</li>
     *   <li>기준 충족 + 진행 중 예보 있음 → 값 갱신. 비 시작이 앞당겨졌을 때만
     *       {@link ForecastRainStartAdvancedEvent}</li>
     *   <li>기준 미달 + 진행 중 예보 있음 → 취소 ({@link ForecastCancelledEvent})</li>
     *   <li>기준 미달 + 진행 중 예보 없음 → 무시</li>
     * </ul>
     *
     * @param date 비 오는 날
     * @param maxPop 강수확률 최댓값 (%)
     * @param totalMm 강수량 합계 (mm)
     * @param firstRainAt 비 시작 시각 (없으면 그날 09:00)
     * @param simulated 시연용 가상 예보 여부
     * @return 반영 결과
     */
    @Transactional
    public ForecastApplyResult apply(LocalDate date, int maxPop, BigDecimal totalMm,
            LocalDateTime firstRainAt, boolean simulated) {
        Optional<RainForecast> active = rainForecastRepository.findActive(date, simulated);
        if (!meetsThreshold(maxPop, totalMm)) {
            return active.map(this::cancel)
                    .orElseGet(() -> new ForecastApplyResult(Applied.IGNORED, null));
        }

        LocalDateTime rainStartAt = firstRainAt;
        if (rainStartAt == null) {
            rainStartAt = date.atTime(DEFAULT_RAIN_START);
            log.warn("[비 예보] {} 비 시작 시각을 몰라 임시로 {}를 씁니다.", date, rainStartAt);
        }
        BigDecimal mm = totalMm.min(MAX_MM).setScale(2, RoundingMode.HALF_UP);
        LocalDateTime fetchedAt = simulated ? null : LocalDateTime.now(clock);

        if (active.isEmpty()) {
            RainForecast forecast = rainForecastRepository.save(RainForecast.builder()
                    .rainDate(date)
                    .rainStartAt(rainStartAt)
                    .pop(maxPop)
                    .pcpMm(mm)
                    .isSimulated(simulated)
                    .fetchedAt(fetchedAt)
                    .build());
            log.info("[비 예보] 확정 id={} {} 비 시작 {} ({}%, {}mm, 가상={})",
                    forecast.getId(), date, rainStartAt, maxPop, mm, simulated);
            eventPublisher.publishEvent(new ForecastConfirmedEvent(forecast.getId(), simulated));
            return new ForecastApplyResult(Applied.CONFIRMED, forecast.getId());
        }

        RainForecast forecast = active.get();
        LocalDateTime previousStart = forecast.getRainStartAt();
        forecast.refresh(maxPop, mm, rainStartAt, fetchedAt);
        if (rainStartAt.isBefore(previousStart)) {
            log.info("[비 예보] 비 시작 앞당김 id={} {} → {}", forecast.getId(), previousStart,
                    rainStartAt);
            eventPublisher.publishEvent(new ForecastRainStartAdvancedEvent(
                    forecast.getId(), previousStart, rainStartAt));
        }
        return new ForecastApplyResult(Applied.UPDATED, forecast.getId());
    }

    /**
     * 시연용 가상 예보를 적용합니다. 비 시작 날짜의 가상 예보로 {@link #apply}와 같은 기준을 씁니다.
     *
     * @param pop 강수확률 최댓값 (%)
     * @param mm 강수량 합계 (mm)
     * @param rainStartAt 비 시작 시각 (지금 + 당일 예보 최소 남은 시간 이후)
     * @return 반영 결과
     * @throws BusinessException 비 시작이 너무 가까움 (INVALID_INPUT)
     */
    @Transactional
    public ForecastApplyResult simulate(int pop, BigDecimal mm, LocalDateTime rainStartAt) {
        LocalDateTime earliest =
                LocalDateTime.now(clock).plusHours(policy.sameDayMinLeadHours());
        if (rainStartAt.isBefore(earliest)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "비 시작 시각은 지금부터 " + policy.sameDayMinLeadHours() + "시간 뒤 이후여야 해요.");
        }
        return apply(rainStartAt.toLocalDate(), pop, mm, rainStartAt, true);
    }

    private ForecastApplyResult cancel(RainForecast forecast) {
        forecast.cancel();
        log.info("[비 예보] 기준 미달로 취소 id={} {}", forecast.getId(), forecast.getRainDate());
        eventPublisher.publishEvent(new ForecastCancelledEvent(forecast.getId()));
        return new ForecastApplyResult(Applied.CANCELLED, forecast.getId());
    }

    private boolean meetsThreshold(int maxPop, BigDecimal totalMm) {
        return maxPop >= policy.rainPopThreshold()
                && totalMm.compareTo(policy.rainMmThreshold()) >= 0;
    }
}
