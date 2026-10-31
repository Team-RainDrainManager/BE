package com.rainbutler.global.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 위경도 거리 계산 유틸입니다. (대신 점검 반경 등)
 *
 * <p>DB에서 넓게 거른 뒤({@link #latDelta} · {@link #lngDelta} 사각형) {@link #meters}로 정확히 거릅니다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GeoDistance {

    private static final double EARTH_RADIUS_METERS = 6_371_000;
    /** 위도 1도의 거리 (m) */
    private static final double METERS_PER_DEGREE = 111_320;
    /** 사각형 후보를 반경보다 조금 넓게 잡는 여유 */
    private static final double MARGIN = 1.1;

    /**
     * 두 지점 사이 거리를 구합니다. (하버사인 공식)
     *
     * @return 거리 (m)
     */
    public static double meters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /**
     * 반경을 덮는 위도 폭(도)을 구합니다.
     *
     * @param meters 반경 (m)
     * @return 위도 ± 폭
     */
    public static double latDelta(double meters) {
        return meters / METERS_PER_DEGREE * MARGIN;
    }

    /**
     * 반경을 덮는 경도 폭(도)을 구합니다. 경도 1도의 거리는 위도에 따라 줄어듭니다. (서울 근처 약 88km)
     *
     * @param latitude 기준 위도
     * @param meters 반경 (m)
     * @return 경도 ± 폭
     */
    public static double lngDelta(double latitude, double meters) {
        return meters / (METERS_PER_DEGREE * Math.cos(Math.toRadians(latitude))) * MARGIN;
    }
}
