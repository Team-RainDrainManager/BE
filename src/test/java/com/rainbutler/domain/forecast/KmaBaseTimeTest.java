package com.rainbutler.domain.forecast;

import static org.assertj.core.api.Assertions.assertThat;

import com.rainbutler.domain.forecast.client.KmaBaseTime;
import java.time.LocalDateTime;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 단기예보 발표 시각(발표 10분 뒤부터 조회 가능) 계산을 검증합니다.
 */
class KmaBaseTimeTest {

    @ParameterizedTest
    @CsvSource({
            "2026-10-30T02:09:59, 2026-10-29T23:00",
            "2026-10-30T02:10:00, 2026-10-30T02:00",
            "2026-10-30T02:11:00, 2026-10-30T02:00",
            "2026-10-30T00:30:00, 2026-10-29T23:00",
            "2026-10-30T05:15:00, 2026-10-30T05:00",
            "2026-10-30T14:30:00, 2026-10-30T14:00",
            "2026-10-30T23:09:00, 2026-10-30T20:00",
            "2026-10-30T23:10:00, 2026-10-30T23:00",
            "2026-01-01T01:00:00, 2025-12-31T23:00"
    })
    void 지금_조회할_수_있는_가장_최근_발표_시각을_고른다(
            LocalDateTime now, LocalDateTime expected) {
        assertThat(KmaBaseTime.latest(now)).isEqualTo(expected);
    }
}
