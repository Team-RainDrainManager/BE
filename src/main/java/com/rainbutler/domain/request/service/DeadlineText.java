package com.rainbutler.domain.request.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 알림 문구에 넣을 마감 시각 표현입니다. (오늘 20:00 · 내일 14:00 · 11월 2일 14:00)
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DeadlineText {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("M월 d일 HH:mm");

    /**
     * 기준 날짜와 비교해 마감 시각을 읽기 쉽게 씁니다.
     *
     * @param deadline 마감 시각
     * @param today 오늘
     * @return 예: "오늘 20:00"
     */
    public static String of(LocalDateTime deadline, LocalDate today) {
        LocalDate date = deadline.toLocalDate();
        if (date.equals(today)) {
            return "오늘 " + deadline.format(TIME);
        }
        if (date.equals(today.plusDays(1))) {
            return "내일 " + deadline.format(TIME);
        }
        return deadline.format(DATE_TIME);
    }
}
