package com.rainbutler.domain.weather.repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 현재 날씨 1행({@code current_weather}) 저장소입니다. 인프라 테이블이라 엔티티 없이 JdbcTemplate로 다룹니다.
 *
 * <p>관측 값 저장과 시연용 강제 비 설정은 서로의 값을 덮어쓰지 않습니다.
 */
@Repository
@RequiredArgsConstructor
public class CurrentWeatherRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 현재 날씨 1행입니다.
     *
     * @param pty 강수형태
     * @param rn1Mm 1시간 강수량 (mm)
     * @param raining 비 오는 중 (관측 기준)
     * @param observedAt 실황 기준 시각
     * @param fetchedAt 마지막 반영 시각
     * @param simulatedRainingUntil 시연용 강제 비 종료 시각
     */
    public record CurrentWeather(Integer pty, BigDecimal rn1Mm, boolean raining,
            LocalDateTime observedAt, LocalDateTime fetchedAt,
            LocalDateTime simulatedRainingUntil) {
    }

    /**
     * 현재 날씨를 읽습니다.
     *
     * @return 현재 날씨 (아직 한 번도 수집 · 설정하지 않았으면 비어 있음)
     */
    public Optional<CurrentWeather> find() {
        List<CurrentWeather> rows = jdbcTemplate.query("SELECT pty, rn1_mm, raining, observed_at, "
                + "fetched_at, simulated_raining_until FROM current_weather WHERE id = 1",
                (rs, n) -> new CurrentWeather(
                        (Integer) rs.getObject("pty", Integer.class),
                        rs.getBigDecimal("rn1_mm"),
                        rs.getBoolean("raining"),
                        toLocal(rs.getTimestamp("observed_at")),
                        toLocal(rs.getTimestamp("fetched_at")),
                        toLocal(rs.getTimestamp("simulated_raining_until"))));
        return rows.stream().findFirst();
    }

    /**
     * 관측 값을 저장합니다. 시연용 강제 비 설정은 유지합니다.
     */
    public void saveObservation(Integer pty, BigDecimal rn1Mm, boolean raining,
            LocalDateTime observedAt, LocalDateTime fetchedAt) {
        jdbcTemplate.update("INSERT INTO current_weather (id, pty, rn1_mm, raining, observed_at, "
                + "fetched_at) VALUES (1, ?, ?, ?, ?, ?) ON CONFLICT (id) DO UPDATE SET "
                + "pty = EXCLUDED.pty, rn1_mm = EXCLUDED.rn1_mm, raining = EXCLUDED.raining, "
                + "observed_at = EXCLUDED.observed_at, fetched_at = EXCLUDED.fetched_at",
                pty, rn1Mm, raining, toTimestamp(observedAt), toTimestamp(fetchedAt));
    }

    /**
     * 시연용 강제 비 종료 시각을 설정합니다. 관측 값은 유지합니다.
     *
     * @param until 종료 시각 (null이면 해제)
     * @param now 행이 없을 때 반영 시각으로 쓸 현재 시각
     */
    public void saveSimulatedRainingUntil(LocalDateTime until, LocalDateTime now) {
        jdbcTemplate.update("INSERT INTO current_weather (id, raining, fetched_at, "
                + "simulated_raining_until) VALUES (1, false, ?, ?) ON CONFLICT (id) DO UPDATE SET "
                + "simulated_raining_until = EXCLUDED.simulated_raining_until",
                toTimestamp(now), toTimestamp(until));
    }

    private static LocalDateTime toLocal(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static Timestamp toTimestamp(LocalDateTime time) {
        return time == null ? null : Timestamp.valueOf(time);
    }
}
