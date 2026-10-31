package com.rainbutler.domain.request;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 비 예보 요청 통합 테스트용 데이터를 SQL로 만듭니다.
 *
 * <p>테스트 DB는 모든 통합 테스트가 함께 쓰므로, {@link #neutralize()}로 다른 테스트가 남긴 진행 중 데이터
 * (입양 · 운영 중 빗물받이 · 진행 중 요청 · 예보 · 미발송 알림)를 먼저 비활성으로 돌립니다.
 */
public class RequestFixtures {

    /** 기준 위치 (역곡동 근처) */
    public static final double BASE_LAT = 37.4853;
    public static final double BASE_LNG = 126.8113;
    /** 위도 1도의 거리 (하버사인, 지구 반지름 6,371km 기준) */
    private static final double METERS_PER_LAT_DEGREE = 6_371_000 * Math.PI / 180;

    private final JdbcTemplate jdbc;

    public RequestFixtures(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 다른 테스트가 남긴 진행 중 데이터를 비활성으로 돌립니다.
     */
    public void neutralize() {
        jdbc.update("UPDATE inspection_request SET status = 'COMPLETED' "
                + "WHERE status IN ('SENT', 'ACCEPTED', 'OPEN', 'CLAIMED')");
        jdbc.update("UPDATE adoption SET status = 'RELEASED' WHERE status = 'ACTIVE'");
        jdbc.update("UPDATE drain SET status = 'REMOVED' WHERE status = 'ACTIVE'");
        jdbc.update("UPDATE rain_forecast SET status = 'CANCELLED' WHERE status = 'ACTIVE'");
        jdbc.update("UPDATE notification SET sent_at = now() WHERE sent_at IS NULL");
    }

    /**
     * 기준 위치에서 북쪽으로 떨어진 위도를 구합니다.
     *
     * @param meters 북쪽 거리 (m)
     * @return 위도
     */
    public static double north(double meters) {
        return BASE_LAT + meters / METERS_PER_LAT_DEGREE;
    }

    /**
     * 온보딩을 마치고 알림 · 야간 푸시 설정을 정한 사용자를 만듭니다.
     */
    public Long user(String nickname, boolean notifications, boolean nightConsent) {
        return jdbc.queryForObject("INSERT INTO users (email, password, nickname, provider, role, "
                + "point, level, onboarding_completed_at, identity_verified_at, "
                + "notification_enabled, night_push_consent, created_at, updated_at) VALUES (?, "
                + "'{noop}x', ?, 'LOCAL', 'USER', 0, 1, now(), now(), ?, ?, now(), now()) "
                + "RETURNING id", Long.class, UUID.randomUUID() + "@test.com", nickname,
                notifications, nightConsent);
    }

    /**
     * 온보딩을 마친 사용자 (알림 켬, 야간 동의)
     */
    public Long user(String nickname) {
        return user(nickname, true, true);
    }

    /**
     * 운영 중 빗물받이를 만듭니다.
     */
    public Long drain(double lat, double lng, boolean risk) {
        Long registrar = user("등록자");
        return jdbc.queryForObject("INSERT INTO drain (registered_by, latitude, longitude, "
                + "photo_url, dirt_level, adoption_status, status, is_risk, created_at, "
                + "updated_at) VALUES (?, ?, ?, 'http://x', 1, 'AVAILABLE', 'ACTIVE', ?, now(), "
                + "now()) RETURNING id", Long.class, registrar, lat, lng, risk);
    }

    /**
     * 빗물받이를 입양합니다. (입양 조건 청소 1건 포함)
     */
    public Long adoption(Long userId, Long drainId, String nickname) {
        Long cleaning = jdbc.queryForObject("INSERT INTO cleaning (user_id, drain_id, type, "
                + "before_photo_url, status, review_status, latitude, longitude, "
                + "location_mismatch, started_at, created_at, updated_at) VALUES (?, ?, "
                + "'ADOPTION', 'http://x', 'COMPLETED', 'NOT_REQUIRED', 37.5, 127.0, false, "
                + "now(), now(), now()) RETURNING id", Long.class, userId, drainId);
        jdbc.update("UPDATE drain SET adoption_status = 'ADOPTED' WHERE id = ?", drainId);
        return jdbc.queryForObject("INSERT INTO adoption (user_id, drain_id, cleaning_id, "
                + "nickname, status, level, score, ignore_count, adopted_at, created_at, "
                + "updated_at) VALUES (?, ?, ?, ?, 'ACTIVE', 1, 0, 0, now(), now(), now()) "
                + "RETURNING id", Long.class, userId, drainId, cleaning, nickname);
    }

    /**
     * 진행 중(ACTIVE) 예보를 만듭니다.
     */
    public Long forecast(LocalDate rainDate, LocalDateTime rainStartAt, boolean simulated) {
        return jdbc.queryForObject("INSERT INTO rain_forecast (rain_date, rain_start_at, pop, "
                + "pcp_mm, status, is_simulated, created_at, updated_at) VALUES (?, ?, 80, 10, "
                + "'ACTIVE', ?, now(), now()) RETURNING id", Long.class, rainDate,
                Timestamp.valueOf(rainStartAt), simulated);
    }

    /**
     * 요청을 직접 만듭니다.
     */
    public Long request(Long forecastId, Long drainId, Long adoptionId, String status,
            LocalDateTime replyDeadline, LocalDateTime completeDeadline) {
        return jdbc.queryForObject("INSERT INTO inspection_request (forecast_id, drain_id, "
                + "adoption_id, status, sent_at, reply_deadline_at, complete_deadline_at, "
                + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, now(), now()) "
                + "RETURNING id", Long.class, forecastId, drainId, adoptionId, status,
                Timestamp.valueOf(replyDeadline.minusHours(6)), Timestamp.valueOf(replyDeadline),
                Timestamp.valueOf(completeDeadline));
    }

    /**
     * 요청을 대신 점검자가 맡은 상태로 바꿉니다.
     */
    public void claim(Long requestId, Long substituteUserId) {
        jdbc.update("UPDATE inspection_request SET status = 'CLAIMED', substitute_user_id = ?, "
                + "claimed_at = now() WHERE id = ?", substituteUserId, requestId);
    }

    /**
     * 사용자를 제재 중으로 만듭니다.
     */
    public void sanction(Long userId) {
        jdbc.update("INSERT INTO sanction (user_id, reason, starts_at, until, permanent, "
                + "created_at, updated_at) VALUES (?, '허위 제보', '2000-01-01', null, true, now(), "
                + "now())", userId);
    }

    /**
     * 요청 상태를 읽습니다.
     */
    public String status(Long requestId) {
        return jdbc.queryForObject(
                "SELECT status FROM inspection_request WHERE id = ?", String.class, requestId);
    }

    /**
     * 사용자가 받은 알림 수를 셉니다.
     */
    public int notificationCount(Long userId, String type) {
        return jdbc.queryForObject("SELECT count(*) FROM notification WHERE user_id = ? "
                + "AND type = ?", Integer.class, userId, type);
    }

    /**
     * 사용자가 받은 그 종류의 가장 최근 알림 (제목 + " | " + 내용)
     */
    public String lastNotification(Long userId, String type) {
        return jdbc.queryForObject("SELECT title || ' | ' || body FROM notification "
                + "WHERE user_id = ? AND type = ? ORDER BY id DESC LIMIT 1", String.class,
                userId, type);
    }
}
