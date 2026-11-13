package com.rainbutler.global.geo;

import static org.assertj.core.api.Assertions.assertThat;

import com.rainbutler.global.geo.KmaGridConverter.Grid;
import org.junit.jupiter.api.Test;

/**
 * 기상청 격자 변환을 검증합니다. 기대값은 기상청 "단기예보 격자 위경도" 엑셀의 값입니다.
 */
class KmaGridConverterTest {

    @Test
    void 역곡동은_57_125() {
        assertThat(KmaGridConverter.toGrid(37.4855, 126.8115)).isEqualTo(new Grid(57, 125));
    }

    @Test
    void 엑셀의_시도_대표_좌표와_같다() {
        // 서울특별시
        assertThat(KmaGridConverter.toGrid(37.5635694, 126.9800083))
                .isEqualTo(new Grid(60, 127));
        // 부산광역시
        assertThat(KmaGridConverter.toGrid(35.1770194, 129.0769528))
                .isEqualTo(new Grid(98, 76));
        // 제주특별자치도
        assertThat(KmaGridConverter.toGrid(33.4856944, 126.5003333))
                .isEqualTo(new Grid(52, 38));
    }
}
