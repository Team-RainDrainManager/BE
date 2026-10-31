package com.rainbutler.domain.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.rainbutler.global.util.GeoDistance;
import com.rainbutler.global.util.KoreanJosa;
import org.junit.jupiter.api.Test;

/**
 * 거리 계산 · 조사 유틸을 검증합니다.
 */
class GeoAndJosaTest {

    @Test
    void 북쪽으로_떨어진_거리를_계산한다() {
        double lat = RequestFixtures.north(300);

        assertThat(GeoDistance.meters(RequestFixtures.BASE_LAT, RequestFixtures.BASE_LNG,
                lat, RequestFixtures.BASE_LNG)).isCloseTo(300, within(0.01));
    }

    @Test
    void 사각형_폭은_반경을_덮는다() {
        double lngDelta = GeoDistance.lngDelta(RequestFixtures.BASE_LAT, 300);
        double eastMeters = GeoDistance.meters(RequestFixtures.BASE_LAT, RequestFixtures.BASE_LNG,
                RequestFixtures.BASE_LAT, RequestFixtures.BASE_LNG + lngDelta);
        double northMeters = GeoDistance.meters(RequestFixtures.BASE_LAT, RequestFixtures.BASE_LNG,
                RequestFixtures.BASE_LAT + GeoDistance.latDelta(300), RequestFixtures.BASE_LNG);

        assertThat(eastMeters).isGreaterThan(300);
        assertThat(northMeters).isGreaterThan(300);
    }

    @Test
    void 받침에_맞게_을_를을_붙인다() {
        assertThat(KoreanJosa.eulReul("또랑이")).isEqualTo("또랑이를");
        assertThat(KoreanJosa.eulReul("빗물몽")).isEqualTo("빗물몽을");
        assertThat(KoreanJosa.eulReul("Drain")).isEqualTo("Drain를");
    }
}
