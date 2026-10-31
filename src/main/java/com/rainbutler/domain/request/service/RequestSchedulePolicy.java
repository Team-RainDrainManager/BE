package com.rainbutler.domain.request.service;

import com.rainbutler.domain.forecast.entity.RainForecast;
import com.rainbutler.global.config.PolicyProperties;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 비 예보 요청의 발송 시각과 응답 · 완료 마감을 정합니다. <b>발송 · 마감 규칙은 이 클래스에서만 결정합니다.</b>
 *
 * <p>시각 값은 모두 {@code rain-butler.policy}에서 읽습니다. 아래 예시는 기본값(06:00 발송, 12:00 응답 마감,
 * 20:00 완료 마감, 3시간 · 1시간 · 1시간, 상한 21:00) 기준입니다.
 *
 * <h3>1. 기본 흐름 — 내일 비를 오늘 06:00 전에 알았을 때</h3>
 * 06:00 일괄 발송 배치가 보냅니다 ({@link #noticeSchedule}). 응답 마감 오늘 12:00, 완료 마감 오늘 20:00.
 * <br>예: 오늘 05:15 수집에서 내일 15시 비 확정 → 06:00 발송.
 *
 * <h3>2. 즉시 발송 — 오늘 비, 또는 06:00 이후에 알게 된 내일 비</h3>
 * 낮(06:00 ~ 21:00)에 알게 되면 바로 보냅니다 ({@link #plan}의 SEND_NOW).
 * <ul>
 *   <li>비 시작까지 3시간 미만이면 보내지 않습니다. 예: 14:01에 17:00 비 → SKIP, 14:00에 17:00 비 → 발송</li>
 *   <li>완료 마감 = 비 시작 1시간 전. 그 시각이 21:00 이후이거나 06:00 이전(밤)이면 직전 21:00로 당깁니다
 *       ({@link #completeDeadlineBefore}).
 *       예: 비 23:30 → 21:00 / 비 다음 날 03:00 → 전날 21:00 / 비 다음 날 15:00 → 다음 날 14:00</li>
 *   <li>응답 마감 = 지금 + 1시간. 완료 마감보다 늦으면 완료 마감과 같게 합니다.</li>
 *   <li>완료 마감이 지금이거나 이미 지났으면 보내지 않습니다. 예: 21:10에 오늘 23:30 비 → SKIP</li>
 * </ul>
 *
 * <h3>3. 밤(21:00 ~ 06:00)에 알게 된 비</h3>
 * 응답 마감(지금 + 1시간)이 한밤중이 되므로 바로 보내지 않고 다음 06:00 일괄 발송 배치에 맡깁니다.
 * 배치는 내일 비에는 1번 규칙, 오늘 비에는 06:00 기준 2번 규칙을 씁니다.
 * <br>예: 23:15에 내일 15시 비 → 다음 날 06:00에 2번 규칙(응답 07:00, 완료 14:00)으로 발송.
 * 단, 21:00 이후에 알게 된 오늘 밤 비는 완료 마감(21:00)이 이미 지나 보내지 않습니다.
 */
@Component
@RequiredArgsConstructor
public class RequestSchedulePolicy {

    private final PolicyProperties policy;

    /**
     * 예보가 확정(또는 06:00 배치에서 오늘 예보를 확인)될 때 언제 보낼지 정합니다.
     *
     * @param forecast 비 예보
     * @param now 현재 시각
     * @return 06:00 일괄 발송 / 즉시 발송 / 보내지 않음
     */
    public RequestSchedule plan(RainForecast forecast, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        LocalDate rainDate = forecast.getRainDate();
        if (rainDate.isBefore(today)) {
            return RequestSchedule.skip("지난 날짜의 예보");
        }
        if (rainDate.isAfter(today.plusDays(1))) {
            // 모레 이후 비는 그 전날 06:00 배치가 보낸다
            return RequestSchedule.atNotice();
        }
        boolean beforeNotice = now.toLocalTime().isBefore(policy.rainNoticeTime());
        if (rainDate.equals(today.plusDays(1)) && beforeNotice) {
            return RequestSchedule.atNotice();
        }
        boolean lateNight = !now.toLocalTime().isBefore(policy.latestCompleteTime());
        if (beforeNotice || lateNight) {
            if (rainDate.equals(today) && lateNight) {
                return RequestSchedule.skip("오늘 밤 비는 완료 마감 상한이 지나 보낼 수 없음");
            }
            return RequestSchedule.atNotice();
        }
        return sameDay(forecast.getRainStartAt(), now);
    }

    /**
     * 06:00 일괄 발송 배치가 내일 비 요청에 쓰는 일정입니다. (응답 마감 오늘 12:00, 완료 마감 오늘 20:00)
     *
     * @param now 발송 시각
     * @return 즉시 발송 일정
     */
    public RequestSchedule noticeSchedule(LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        return RequestSchedule.sendNow(now,
                today.atTime(policy.rainReplyDeadlineTime()),
                today.atTime(policy.rainCompleteDeadlineTime()));
    }

    /**
     * 비 시작 시각으로 완료 마감을 구합니다. 비 시작 1시간 전이 밤(상한 21:00 이후 ~ 06:00 이전)에 걸리면
     * 그 직전 21:00로 당깁니다. 비가 앞당겨졌을 때 마감 당기기에도 같은 규칙을 씁니다.
     *
     * @param rainStartAt 비 시작 시각
     * @return 완료 마감
     */
    public LocalDateTime completeDeadlineBefore(LocalDateTime rainStartAt) {
        LocalDateTime candidate =
                rainStartAt.minusHours(policy.sameDayCompleteBeforeRainHours());
        LocalTime time = candidate.toLocalTime();
        LocalTime latest = policy.latestCompleteTime();
        if (time.isAfter(latest)) {
            return candidate.toLocalDate().atTime(latest);
        }
        if (time.isBefore(policy.rainNoticeTime())) {
            return candidate.toLocalDate().minusDays(1).atTime(latest);
        }
        return candidate;
    }

    private RequestSchedule sameDay(LocalDateTime rainStartAt, LocalDateTime now) {
        if (rainStartAt.isBefore(now.plusHours(policy.sameDayMinLeadHours()))) {
            return RequestSchedule.skip("비 시작까지 " + policy.sameDayMinLeadHours()
                    + "시간 미만");
        }
        LocalDateTime completeDeadline = completeDeadlineBefore(rainStartAt);
        if (!completeDeadline.isAfter(now)) {
            return RequestSchedule.skip("완료 마감이 이미 지남");
        }
        LocalDateTime replyDeadline = now.plusHours(policy.sameDayReplyHours());
        if (replyDeadline.isAfter(completeDeadline)) {
            replyDeadline = completeDeadline;
        }
        return RequestSchedule.sendNow(now, replyDeadline, completeDeadline);
    }
}
