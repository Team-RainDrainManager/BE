package com.rainbutler.domain.forecast.client;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 기상청 단기예보 1시간 강수량(PCP) 문자열을 mm 숫자로 바꿉니다.
 *
 * <ul>
 *   <li>{@code 강수없음} → 0</li>
 *   <li>{@code 1mm 미만} · {@code 1.0mm 미만} → 0.5</li>
 *   <li>{@code 2.0mm} → 2.0, 단위 없는 숫자 {@code 1} → 1 (실제 응답에 섞여 온다)</li>
 *   <li>{@code 30.0~50.0mm} → 30.0 (범위는 아래 값)</li>
 *   <li>{@code 50.0mm 이상} → 50.0</li>
 *   <li>모르는 형식 → 경고 로그 + 0</li>
 * </ul>
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PcpParser {

    private static final String NO_RAIN = "강수없음";
    private static final BigDecimal UNDER_ONE_MM = new BigDecimal("0.5");
    private static final Pattern UNDER = Pattern.compile("^\\d+(\\.\\d+)?\\s*mm\\s*미만$");
    private static final Pattern NUMBER =
            Pattern.compile("^(\\d+(?:\\.\\d+)?)\\s*(?:mm(?:\\s*이상)?)?$");
    private static final Pattern RANGE =
            Pattern.compile("^(\\d+(?:\\.\\d+)?)\\s*~\\s*\\d+(?:\\.\\d+)?\\s*mm$");

    /**
     * 강수량 문자열을 mm로 바꿉니다.
     *
     * @param value 기상청 PCP 값
     * @return 강수량 (mm)
     */
    public static BigDecimal parse(String value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        String text = value.strip();
        if (text.isEmpty() || NO_RAIN.equals(text)) {
            return BigDecimal.ZERO;
        }
        if (UNDER.matcher(text).matches()) {
            return UNDER_ONE_MM;
        }
        Matcher range = RANGE.matcher(text);
        if (range.matches()) {
            return new BigDecimal(range.group(1));
        }
        Matcher number = NUMBER.matcher(text);
        if (number.matches()) {
            return new BigDecimal(number.group(1));
        }
        log.warn("[기상청 강수량] 모르는 형식이라 0으로 봅니다: {}", text);
        return BigDecimal.ZERO;
    }
}
