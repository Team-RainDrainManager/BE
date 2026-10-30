package com.rainbutler.domain.forecast.client;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 단기예보 발표 시각(base_date · base_time)을 계산합니다.
 *
 * <p>단기예보는 02 · 05 · 08 · 11 · 14 · 17 · 20 · 23시에 발표하고, 발표 후 10분이 지나야 조회할 수 있습니다.
 * 그래서 "지금 - 10분" 기준으로 가장 최근 발표 시각을 고릅니다. (02:09 → 전날 23시, 02:10 → 02시)
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class KmaBaseTime {

    private static final List<Integer> ISSUE_HOURS = List.of(23, 20, 17, 14, 11, 8, 5, 2);
    private static final int AVAILABLE_AFTER_MINUTES = 10;

    /**
     * 지금 조회할 수 있는 가장 최근 발표 시각을 구합니다.
     *
     * @param now 현재 시각 (한국 시간)
     * @return 발표 시각 (분 · 초는 0)
     */
    public static LocalDateTime latest(LocalDateTime now) {
        LocalDateTime reference = now.minusMinutes(AVAILABLE_AFTER_MINUTES);
        for (int hour : ISSUE_HOURS) {
            LocalDateTime issue = reference.toLocalDate().atTime(LocalTime.of(hour, 0));
            if (!issue.isAfter(reference)) {
                return issue;
            }
        }
        // 00:00 ~ 02:09 → 전날 23시 발표
        return reference.toLocalDate().minusDays(1).atTime(23, 0);
    }
}
