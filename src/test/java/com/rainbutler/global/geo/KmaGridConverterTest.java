package com.rainbutler.global.geo;

import static org.assertj.core.api.Assertions.assertThat;

import com.rainbutler.global.geo.KmaGridConverter.Grid;
import org.junit.jupiter.api.Test;

/**
 * 기상청 격자 변환을 검증합니다.
 *
 * <p>역곡동은 운영 중인 파일럿 격자(57, 125), 서울시청은 기상청 변환 공식 예제에서 널리 쓰는 값(60, 127)입니다.
 * 다른 지역은 기상청 "단기예보 격자 위경도" 엑셀로 확인한 뒤 추가합니다.
 */
class KmaGridConverterTest {

    @Test
    void 역곡동은_57_125() {
        assertThat(KmaGridConverter.toGrid(37.4855, 126.8115)).isEqualTo(new Grid(57, 125));
    }

    @Test
    void 서울시청은_60_127() {
        assertThat(KmaGridConverter.toGrid(37.5635694, 126.9800083))
                .isEqualTo(new Grid(60, 127));
    }
}
