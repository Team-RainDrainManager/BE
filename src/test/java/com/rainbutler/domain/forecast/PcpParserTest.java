package com.rainbutler.domain.forecast;

import static org.assertj.core.api.Assertions.assertThat;

import com.rainbutler.domain.forecast.client.PcpParser;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 기상청 1시간 강수량(PCP) 문자열 파싱을 검증합니다.
 */
class PcpParserTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "강수없음|0",
            "1mm 미만|0.5",
            "1.0mm 미만|0.5",
            "2.0mm|2.0",
            "7mm|7",
            "30.0~50.0mm|30.0",
            "50.0mm 이상|50.0",
            " 3.5mm |3.5"
    })
    void 기상청_강수량_형식을_mm로_바꾼다(String value, String expected) {
        assertThat(PcpParser.parse(value)).isEqualByComparingTo(new BigDecimal(expected));
    }

    @Test
    void 모르는_형식과_빈_값은_0이다() {
        assertThat(PcpParser.parse("많음")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(PcpParser.parse("")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(PcpParser.parse(null)).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
