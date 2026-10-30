package com.rainbutler.domain.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.rainbutler.domain.forecast.entity.ForecastStatus;
import com.rainbutler.domain.forecast.entity.RainForecast;
import com.rainbutler.domain.forecast.event.ForecastCancelledEvent;
import com.rainbutler.domain.forecast.event.ForecastConfirmedEvent;
import com.rainbutler.domain.forecast.event.ForecastRainStartAdvancedEvent;
import com.rainbutler.domain.forecast.repository.RainForecastRepository;
import com.rainbutler.domain.forecast.service.ForecastApplyResult;
import com.rainbutler.domain.forecast.service.ForecastApplyResult.Applied;
import com.rainbutler.domain.forecast.service.ForecastService;
import com.rainbutler.support.IntegrationTestSupport;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/**
 * 예보 확정 · 갱신 · 취소와 이벤트 발행을 검증합니다. (기준 60% · 5mm, 시각은 Clock으로 고정)
 *
 * <p>테스트마다 다른 날짜를 써서 진행 중 예보 유니크 제약이 서로 부딪히지 않게 합니다.
 */
@RecordApplicationEvents
class ForecastServiceTest extends IntegrationTestSupport {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 30, 5, 15);
    private static final AtomicInteger DAY = new AtomicInteger();

    @Autowired
    private ForecastService forecastService;

    @Autowired
    private RainForecastRepository rainForecastRepository;

    @Autowired
    private ApplicationEvents events;

    @MockitoBean
    private Clock clock;

    private LocalDate date;

    @BeforeEach
    void setUp() {
        given(clock.getZone()).willReturn(SEOUL);
        given(clock.instant()).willReturn(NOW.atZone(SEOUL).toInstant());
        date = LocalDate.of(2035, 1, 1).plusDays(DAY.incrementAndGet());
    }

    @Test
    void 기준을_넘으면_새_예보를_확정하고_확정_이벤트를_한_번_발행한다() {
        ForecastApplyResult result = apply(80, "12.345", at(15));

        assertThat(result.applied()).isEqualTo(Applied.CONFIRMED);
        RainForecast forecast = rainForecastRepository.findById(result.forecastId()).orElseThrow();
        assertThat(forecast.getStatus()).isEqualTo(ForecastStatus.ACTIVE);
        assertThat(forecast.getPop()).isEqualTo(80);
        assertThat(forecast.getPcpMm()).isEqualByComparingTo("12.35");
        assertThat(forecast.getRainStartAt()).isEqualTo(at(15));
        assertThat(forecast.getFetchedAt()).isEqualTo(NOW);
        assertThat(forecast.isSimulated()).isFalse();
        assertThat(events.stream(ForecastConfirmedEvent.class))
                .containsExactly(new ForecastConfirmedEvent(result.forecastId(), false));
    }

    @Test
    void 진행_중_예보는_값만_갱신하고_비_시작이_늦춰져도_이벤트가_없다() {
        Long id = apply(70, "6", at(12)).forecastId();

        ForecastApplyResult result = apply(90, "20", at(15));

        assertThat(result).isEqualTo(new ForecastApplyResult(Applied.UPDATED, id));
        RainForecast forecast = rainForecastRepository.findById(id).orElseThrow();
        assertThat(forecast.getPop()).isEqualTo(90);
        assertThat(forecast.getPcpMm()).isEqualByComparingTo("20");
        assertThat(forecast.getRainStartAt()).isEqualTo(at(15));
        assertThat(events.stream(ForecastConfirmedEvent.class)).hasSize(1);
        assertThat(events.stream(ForecastRainStartAdvancedEvent.class)).isEmpty();
        assertThat(events.stream(ForecastCancelledEvent.class)).isEmpty();
    }

    @Test
    void 비_시작이_앞당겨지면_앞당김_이벤트를_발행한다() {
        Long id = apply(70, "6", at(15)).forecastId();

        apply(70, "6", at(11));

        assertThat(events.stream(ForecastRainStartAdvancedEvent.class))
                .containsExactly(new ForecastRainStartAdvancedEvent(id, at(15), at(11)));
    }

    @Test
    void 기준_미달로_바뀌면_진행_중_예보를_취소하고_취소_이벤트를_발행한다() {
        Long id = apply(70, "6", at(15)).forecastId();

        ForecastApplyResult result = apply(30, "0", null);

        assertThat(result).isEqualTo(new ForecastApplyResult(Applied.CANCELLED, id));
        assertThat(rainForecastRepository.findById(id).orElseThrow().getStatus())
                .isEqualTo(ForecastStatus.CANCELLED);
        assertThat(events.stream(ForecastCancelledEvent.class))
                .containsExactly(new ForecastCancelledEvent(id));
    }

    @Test
    void 기준_미달이고_진행_중_예보가_없으면_무시한다() {
        assertThat(apply(30, "0", null))
                .isEqualTo(new ForecastApplyResult(Applied.IGNORED, null));
        assertThat(events.stream()).noneMatch(event -> event.getClass().getPackageName()
                .endsWith("forecast.event"));
    }

    @Test
    void 취소된_예보는_되살리지_않고_다시_기준을_넘으면_새_행을_만든다() {
        Long first = apply(70, "6", at(15)).forecastId();
        apply(30, "0", null);

        ForecastApplyResult again = apply(80, "10", at(16));

        assertThat(again.applied()).isEqualTo(Applied.CONFIRMED);
        assertThat(again.forecastId()).isNotEqualTo(first);
        assertThat(rainForecastRepository.findById(first).orElseThrow().getStatus())
                .isEqualTo(ForecastStatus.CANCELLED);
        assertThat(events.stream(ForecastConfirmedEvent.class)).hasSize(2);
    }

    @Test
    void 기준_경계는_60퍼센트_5mm_이상이다() {
        assertThat(apply(59, "100", at(15)).applied()).isEqualTo(Applied.IGNORED);
        assertThat(apply(100, "4.99", at(15)).applied()).isEqualTo(Applied.IGNORED);
        assertThat(apply(60, "5", at(15)).applied()).isEqualTo(Applied.CONFIRMED);
    }

    @Test
    void 비_시작_시각을_모르면_그날_9시로_둔다() {
        Long id = apply(80, "10", null).forecastId();

        assertThat(rainForecastRepository.findById(id).orElseThrow().getRainStartAt())
                .isEqualTo(at(9));
    }

    @Test
    void 같은_날짜라도_실제_예보와_가상_예보는_따로_확정된다() {
        ForecastApplyResult real = apply(80, "10", at(15));
        ForecastApplyResult simulated =
                forecastService.apply(date, 80, new BigDecimal("10"), at(15), true);

        assertThat(simulated.applied()).isEqualTo(Applied.CONFIRMED);
        assertThat(simulated.forecastId()).isNotEqualTo(real.forecastId());
        RainForecast forecast =
                rainForecastRepository.findById(simulated.forecastId()).orElseThrow();
        assertThat(forecast.isSimulated()).isTrue();
        assertThat(forecast.getFetchedAt()).isNull();
    }

    private ForecastApplyResult apply(int pop, String mm, LocalDateTime firstRainAt) {
        return forecastService.apply(date, pop, new BigDecimal(mm), firstRainAt, false);
    }

    private LocalDateTime at(int hour) {
        return date.atTime(hour, 0);
    }
}
