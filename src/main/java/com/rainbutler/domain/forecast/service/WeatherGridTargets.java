package com.rainbutler.domain.forecast.service;

import com.rainbutler.domain.forecast.client.KmaProperties;
import com.rainbutler.global.geo.KmaGridConverter.Grid;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 기상청 예보 · 실황을 받을 격자 목록을 만듭니다. (예보 수집 · 현재 날씨 수집이 같은 목록을 쓴다)
 *
 * <ul>
 *   <li>운영 중 빗물받이가 있는 격자 (격자가 없는 빗물받이는 기본 격자)</li>
 *   <li>기본 격자는 빗물받이가 없어도 항상 포함 (배너 · 시연 기본값)</li>
 *   <li>{@code max-grids-per-run}을 넘으면 경고 로그 후 입양된 빗물받이가 있는 격자, 빗물받이가 많은 격자 순으로 자른다</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherGridTargets {

    private final JdbcTemplate jdbcTemplate;
    private final KmaProperties kmaProperties;

    /**
     * 수집할 격자 목록입니다. 기본 격자가 맨 앞입니다.
     *
     * @return 격자 목록
     */
    public List<Grid> find() {
        Grid defaultGrid = kmaProperties.defaultGrid();
        // 입양된 빗물받이가 있는 격자 → 빗물받이가 많은 격자 순
        List<Grid> grids = jdbcTemplate.query("SELECT COALESCE(d.grid_nx, ?) AS nx, "
                        + "COALESCE(d.grid_ny, ?) AS ny, "
                        + "bool_or(EXISTS (SELECT 1 FROM adoption a WHERE a.drain_id = d.id "
                        + "AND a.status = 'ACTIVE' AND a.deleted_at IS NULL)) AS adopted, "
                        + "count(*) AS drains "
                        + "FROM drain d WHERE d.status = 'ACTIVE' AND d.deleted_at IS NULL "
                        + "GROUP BY 1, 2 ORDER BY adopted DESC, drains DESC, nx, ny",
                (rs, n) -> new Grid(rs.getInt("nx"), rs.getInt("ny")),
                defaultGrid.nx(), defaultGrid.ny());

        List<Grid> result = new ArrayList<>();
        result.add(defaultGrid);
        grids.stream().filter(grid -> !grid.equals(defaultGrid)).forEach(result::add);
        int max = kmaProperties.maxGridsPerRun();
        if (result.size() > max) {
            log.warn("[기상청 격자] 수집 대상 격자 {}개가 상한 {}개를 넘어 입양된 격자 우선으로 {}개만 수집합니다.",
                    result.size(), max, max);
            return List.copyOf(result.subList(0, max));
        }
        return result;
    }
}
