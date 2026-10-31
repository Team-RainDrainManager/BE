package com.rainbutler.domain.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.rainbutler.domain.forecast.entity.RainForecast;
import com.rainbutler.domain.request.service.RequestSchedule;
import com.rainbutler.domain.request.service.RequestSchedule.Type;
import com.rainbutler.domain.request.service.RequestSchedulePolicy;
import com.rainbutler.support.TestPolicies;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 비 예보 요청 발송 시각 · 마감 규칙을 검증합니다. (기본 정책 값)
 */
class RequestSchedulePolicyTest {

    private final RequestSchedulePolicy policy = new RequestSchedulePolicy(TestPolicies.defaults());

    @Test
    void 내일_비를_06시_전에_알면_06시_일괄_발송이다() {
        RequestSchedule schedule = policy.plan(rain("2026-11-02T15:00"), at("2026-11-01T05:15"));

        assertThat(schedule.type()).isEqualTo(Type.SEND_AT_NOTICE);
    }

    @Test
    void 일괄_발송_일정은_오늘_12시_응답_20시_완료다() {
        RequestSchedule schedule = policy.noticeSchedule(at("2026-11-01T06:00"));

        assertThat(schedule.sentAt()).isEqualTo(at("2026-11-01T06:00"));
        assertThat(schedule.replyDeadlineAt()).isEqualTo(at("2026-11-01T12:00"));
        assertThat(schedule.completeDeadlineAt()).isEqualTo(at("2026-11-01T20:00"));
    }

    @Test
    void 내일_비를_낮에_알면_즉시_발송이고_완료_마감은_비_시작_1시간_전이다() {
        RequestSchedule schedule = policy.plan(rain("2026-11-02T15:00"), at("2026-11-01T14:15"));

        assertThat(schedule.type()).isEqualTo(Type.SEND_NOW);
        assertThat(schedule.sentAt()).isEqualTo(at("2026-11-01T14:15"));
        assertThat(schedule.replyDeadlineAt()).isEqualTo(at("2026-11-01T15:15"));
        assertThat(schedule.completeDeadlineAt()).isEqualTo(at("2026-11-02T14:00"));
    }

    @Test
    void 오늘_비는_비_시작까지_3시간_이상_남아야_보낸다() {
        assertThat(policy.plan(rain("2026-11-01T17:00"), at("2026-11-01T14:01")).type())
                .isEqualTo(Type.SKIP);
        RequestSchedule exact = policy.plan(rain("2026-11-01T17:00"), at("2026-11-01T14:00"));
        assertThat(exact.type()).isEqualTo(Type.SEND_NOW);
        assertThat(exact.completeDeadlineAt()).isEqualTo(at("2026-11-01T16:00"));
    }

    @ParameterizedTest
    @CsvSource({
            "2026-11-01T23:30, 2026-11-01T21:00",
            "2026-11-02T03:00, 2026-11-01T21:00",
            "2026-11-02T06:30, 2026-11-01T21:00",
            "2026-11-02T07:00, 2026-11-02T06:00",
            "2026-11-02T15:00, 2026-11-02T14:00",
            "2026-11-01T22:00, 2026-11-01T21:00"
    })
    void 완료_마감은_밤에_걸리면_직전_21시로_당긴다(LocalDateTime rainStart, LocalDateTime expected) {
        assertThat(policy.completeDeadlineBefore(rainStart)).isEqualTo(expected);
    }

    @Test
    void 응답_마감은_완료_마감을_넘지_않는다() {
        // 18:00에 21:30 비 → 완료 20:30, 응답 19:00 (그대로)
        RequestSchedule schedule = policy.plan(rain("2026-11-01T21:30"), at("2026-11-01T18:00"));
        assertThat(schedule.completeDeadlineAt()).isEqualTo(at("2026-11-01T20:30"));
        assertThat(schedule.replyDeadlineAt()).isEqualTo(at("2026-11-01T19:00"));

        // 20:30에 23:30 비 → 완료 21:00(상한), 응답 21:30이 완료를 넘어 21:00으로
        RequestSchedule tight = policy.plan(rain("2026-11-01T23:30"), at("2026-11-01T20:30"));
        assertThat(tight.type()).isEqualTo(Type.SEND_NOW);
        assertThat(tight.completeDeadlineAt()).isEqualTo(at("2026-11-01T21:00"));
        assertThat(tight.replyDeadlineAt()).isEqualTo(at("2026-11-01T21:00"));
    }

    @Test
    void 밤_21시_이후에_알게_된_오늘_밤_비는_보내지_않는다() {
        assertThat(policy.plan(rain("2026-11-01T23:30"), at("2026-11-01T21:10")).type())
                .isEqualTo(Type.SKIP);
    }

    @Test
    void 밤에_알게_된_내일_비와_새벽에_알게_된_오늘_비는_06시_배치에_맡긴다() {
        assertThat(policy.plan(rain("2026-11-02T15:00"), at("2026-11-01T23:15")).type())
                .isEqualTo(Type.SEND_AT_NOTICE);
        assertThat(policy.plan(rain("2026-11-01T15:00"), at("2026-11-01T02:15")).type())
                .isEqualTo(Type.SEND_AT_NOTICE);
        // 06:00 배치는 오늘 비에 당일 규칙을 쓴다
        RequestSchedule atNotice =
                policy.plan(rain("2026-11-01T15:00"), at("2026-11-01T06:00"));
        assertThat(atNotice.type()).isEqualTo(Type.SEND_NOW);
        assertThat(atNotice.replyDeadlineAt()).isEqualTo(at("2026-11-01T07:00"));
        assertThat(atNotice.completeDeadlineAt()).isEqualTo(at("2026-11-01T14:00"));
    }

    @Test
    void 모레_이후_비는_그_전날_06시_배치가_보내고_지난_날짜는_보내지_않는다() {
        assertThat(policy.plan(rain("2026-11-03T15:00"), at("2026-11-01T14:00")).type())
                .isEqualTo(Type.SEND_AT_NOTICE);
        assertThat(policy.plan(rain("2026-10-31T15:00"), at("2026-11-01T14:00")).type())
                .isEqualTo(Type.SKIP);
    }

    private static RainForecast rain(String rainStartAt) {
        LocalDateTime start = LocalDateTime.parse(rainStartAt);
        return RainForecast.builder()
                .rainDate(start.toLocalDate())
                .rainStartAt(start)
                .pop(80)
                .pcpMm(BigDecimal.TEN)
                .isSimulated(false)
                .build();
    }

    private static LocalDateTime at(String value) {
        return LocalDateTime.parse(value);
    }
}
