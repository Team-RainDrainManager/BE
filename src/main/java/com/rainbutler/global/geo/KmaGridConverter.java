package com.rainbutler.global.geo;

/**
 * 위경도를 기상청 단기예보 격자(nx, ny)로 바꿉니다.
 *
 * <p>기상청 동네예보 활용 가이드의 LCC(람베르트 정각원추) 투영 공식을 그대로 옮겼습니다. 격자 간격 5km, 표준위도
 * 30° · 60°, 기준점 위도 38° · 경도 126° (격자 43, 136).
 */
public final class KmaGridConverter {

    /** 지구 반지름 (km) */
    private static final double EARTH_RADIUS = 6371.00877;
    /** 격자 간격 (km) */
    private static final double GRID = 5.0;
    private static final double SLAT1 = 30.0;
    private static final double SLAT2 = 60.0;
    private static final double OLON = 126.0;
    private static final double OLAT = 38.0;
    private static final double XO = 43;
    private static final double YO = 136;
    private static final double DEGRAD = Math.PI / 180.0;

    // 투영 상수는 입력과 무관하므로 한 번만 계산한다
    private static final double RE = EARTH_RADIUS / GRID;
    private static final double SN;
    private static final double SF;
    private static final double RO;

    static {
        double slat1 = SLAT1 * DEGRAD;
        double slat2 = SLAT2 * DEGRAD;
        double olat = OLAT * DEGRAD;
        double sn = Math.tan(Math.PI * 0.25 + slat2 * 0.5) / Math.tan(Math.PI * 0.25 + slat1 * 0.5);
        SN = Math.log(Math.cos(slat1) / Math.cos(slat2)) / Math.log(sn);
        double sf = Math.tan(Math.PI * 0.25 + slat1 * 0.5);
        SF = Math.pow(sf, SN) * Math.cos(slat1) / SN;
        double ro = Math.tan(Math.PI * 0.25 + olat * 0.5);
        RO = RE * SF / Math.pow(ro, SN);
    }

    private KmaGridConverter() {
    }

    /**
     * 기상청 격자 좌표입니다.
     *
     * @param nx 격자 X
     * @param ny 격자 Y
     */
    public record Grid(int nx, int ny) {
    }

    /**
     * 위경도를 격자로 바꿉니다.
     *
     * @param latitude 위도
     * @param longitude 경도
     * @return 격자
     */
    public static Grid toGrid(double latitude, double longitude) {
        double ra = Math.tan(Math.PI * 0.25 + latitude * DEGRAD * 0.5);
        ra = RE * SF / Math.pow(ra, SN);
        double theta = longitude * DEGRAD - OLON * DEGRAD;
        if (theta > Math.PI) {
            theta -= 2.0 * Math.PI;
        }
        if (theta < -Math.PI) {
            theta += 2.0 * Math.PI;
        }
        theta *= SN;
        int nx = (int) Math.floor(ra * Math.sin(theta) + XO + 0.5);
        int ny = (int) Math.floor(RO - ra * Math.cos(theta) + YO + 0.5);
        return new Grid(nx, ny);
    }
}
