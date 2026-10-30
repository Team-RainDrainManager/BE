package com.rainbutler.domain.forecast.client;

import com.rainbutler.domain.forecast.client.KmaForecastResponse.Item;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 시간별 예보 항목을 날짜별 {@link DailyRain}으로 묶습니다.
 *
 * <p>강수확률(POP)은 하루 최댓값, 1시간 강수량(PCP)은 하루 합계로 봅니다. 비 시작 시각은 그날 강수량이 0보다 크거나
 * 강수확률이 기준 이상인 첫 시각입니다.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DailyRainAggregator {

    private static final String POP = "POP";
    private static final String PCP = "PCP";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmm");

    /**
     * 예보 항목을 날짜별로 요약합니다. 강수확률이 한 시간도 없는 날짜는 결과에 넣지 않습니다.
     *
     * @param items 예보 항목 (POP · PCP 외 항목은 무시)
     * @param popThreshold 비 시작으로 보는 강수확률 기준 (%)
     * @return 날짜순 하루 요약
     */
    public static List<DailyRain> aggregate(Collection<Item> items, int popThreshold) {
        Map<LocalDate, Day> days = new TreeMap<>();
        for (Item item : items) {
            if (!POP.equals(item.category()) && !PCP.equals(item.category())) {
                continue;
            }
            LocalDateTime at;
            try {
                at = LocalDate.parse(item.fcstDate(), DATE)
                        .atTime(LocalTime.parse(item.fcstTime(), TIME));
            } catch (RuntimeException e) {
                log.warn("[기상청 예보] 날짜 · 시각 형식이 달라 건너뜁니다: {} {}",
                        item.fcstDate(), item.fcstTime());
                continue;
            }
            Day day = days.computeIfAbsent(at.toLocalDate(), date -> new Day());
            if (POP.equals(item.category())) {
                int pop = parsePop(item.fcstValue());
                day.hasPop = true;
                day.maxPop = Math.max(day.maxPop, pop);
                if (pop >= popThreshold) {
                    day.markRain(at);
                }
            } else {
                BigDecimal mm = PcpParser.parse(item.fcstValue());
                day.totalMm = day.totalMm.add(mm);
                if (mm.signum() > 0) {
                    day.markRain(at);
                }
            }
        }

        List<DailyRain> result = new ArrayList<>();
        days.forEach((date, day) -> {
            if (day.hasPop) {
                result.add(new DailyRain(date, day.maxPop, day.totalMm, day.firstRainAt));
            }
        });
        return result;
    }

    private static int parsePop(String value) {
        try {
            return Integer.parseInt(value.strip());
        } catch (RuntimeException e) {
            log.warn("[기상청 강수확률] 숫자가 아니라 0으로 봅니다: {}", value);
            return 0;
        }
    }

    /** 집계 중인 하루 값입니다. */
    private static final class Day {
        private boolean hasPop;
        private int maxPop;
        private BigDecimal totalMm = BigDecimal.ZERO;
        private LocalDateTime firstRainAt;

        private void markRain(LocalDateTime at) {
            if (firstRainAt == null || at.isBefore(firstRainAt)) {
                firstRainAt = at;
            }
        }
    }
}
