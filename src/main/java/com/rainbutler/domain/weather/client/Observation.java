package com.rainbutler.domain.weather.client;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * 초단기실황 한 번의 관측 결과입니다.
 *
 * @param pty 강수형태 (0 없음 · 1 비 · 2 비/눈 · 3 눈 · 5 빗방울 · 6 빗방울눈날림 · 7 눈날림, 없으면 null)
 * @param rn1Mm 1시간 강수량 (mm, 없으면 null)
 * @param observedAt 실황 기준 시각 (발표 시각)
 */
public record Observation(Integer pty, BigDecimal rn1Mm, LocalDateTime observedAt) {

    /**
     * 비로 보는 강수형태입니다. 비 · 비/눈 · 빗방울 · 빗방울눈날림.
     * 눈만 오는 3(눈) · 7(눈날림)은 빗물받이 점검을 막을 정도의 비가 아니어서 비로 보지 않습니다.
     */
    private static final Set<Integer> RAIN_TYPES = Set.of(1, 2, 5, 6);

    /**
     * 지금 비가 오는지 판단합니다.
     *
     * @return 강수형태가 1 · 2 · 5 · 6이면 true
     */
    public boolean raining() {
        return pty != null && RAIN_TYPES.contains(pty);
    }
}
