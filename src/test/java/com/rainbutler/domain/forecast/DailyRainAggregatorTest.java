package com.rainbutler.domain.forecast;

import static org.assertj.core.api.Assertions.assertThat;

import com.rainbutler.domain.forecast.client.DailyRain;
import com.rainbutler.domain.forecast.client.DailyRainAggregator;
import com.rainbutler.domain.forecast.client.KmaForecastResponse;
import com.rainbutler.domain.forecast.client.KmaForecastResponse.Item;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * 기상청 응답 샘플(테스트 리소스)로 날짜별 최댓값 · 합계 · 비 시작 시각 집계를 검증합니다.
 *
 * <p>샘플: 10/30은 비 없음(최대 30%), 10/31은 12시 강수확률 60% · 13시부터 강수(0.5 + 2 + 3 + 30mm).
 */
class DailyRainAggregatorTest {

    @Test
    void 날짜별로_강수확률_최댓값과_강수량_합계와_비_시작_시각을_구한다() throws IOException {
        List<DailyRain> result = DailyRainAggregator.aggregate(sampleItems(), 60);

        assertThat(result).hasSize(2);
        DailyRain dry = result.get(0);
        assertThat(dry.date()).isEqualTo(LocalDate.of(2026, 10, 30));
        assertThat(dry.maxPop()).isEqualTo(30);
        assertThat(dry.totalMm()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(dry.firstRainAt()).isNull();

        DailyRain rainy = result.get(1);
        assertThat(rainy.date()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(rainy.maxPop()).isEqualTo(90);
        assertThat(rainy.totalMm()).isEqualByComparingTo("35.5");
        // 강수량은 13시부터지만 강수확률이 12시에 기준(60%)에 닿는다
        assertThat(rainy.firstRainAt()).isEqualTo(LocalDateTime.of(2026, 10, 31, 12, 0));
    }

    @Test
    void 강수확률_기준이_높으면_강수량이_생기는_시각이_비_시작이다() throws IOException {
        List<DailyRain> result = DailyRainAggregator.aggregate(sampleItems(), 85);

        assertThat(result.get(1).firstRainAt()).isEqualTo(LocalDateTime.of(2026, 10, 31, 13, 0));
    }

    @Test
    void 강수확률이_없는_날짜는_결과에_넣지_않는다() {
        List<DailyRain> result = DailyRainAggregator.aggregate(List.of(
                new Item("PCP", "20261101", "0900", "2.0mm"),
                new Item("TMP", "20261101", "0900", "12")), 60);

        assertThat(result).isEmpty();
    }

    private List<Item> sampleItems() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/kma/vilage-fcst-sample.json")) {
            KmaForecastResponse response =
                    JsonMapper.builder().build().readValue(in, KmaForecastResponse.class);
            return response.response().body().items().item();
        }
    }
}
