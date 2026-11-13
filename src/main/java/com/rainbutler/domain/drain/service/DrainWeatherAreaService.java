package com.rainbutler.domain.drain.service;

import com.rainbutler.domain.drain.repository.DrainRepository;
import com.rainbutler.global.geo.KmaGridConverter;
import com.rainbutler.global.geo.KmaGridConverter.Grid;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 빗물받이의 날씨 지역(기상청 격자 · 행정동 이름)을 채웁니다. (등록 직후 · 기존 데이터 백필)
 *
 * <ul>
 *   <li>격자는 위경도로 계산하므로 외부 호출 없이 항상 채운다</li>
 *   <li>행정동 이름은 카카오 호출이라 초당 호출 수를 제한하고, 실패한 행은 비워 두었다가 다음 백필 때 다시 한다</li>
 *   <li>값이 비어 있는 행만 채우므로 여러 번 돌려도 같다</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DrainWeatherAreaService {

    /** 한 번에 읽는 행 수 */
    static final int BATCH_SIZE = 100;
    /** 카카오 호출 사이 간격 (초당 10회 이하) */
    private static final long REGION_CALL_INTERVAL_MILLIS = 100;

    private final DrainRepository drainRepository;
    private final DrainRegionResolver drainRegionResolver;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 백필 결과입니다.
     *
     * @param gridFilled 격자를 채운 행 수
     * @param regionFilled 행정동 이름을 채운 행 수
     * @param regionFailed 행정동 이름을 찾지 못한 행 수 (다음 백필 때 다시)
     */
    public record BackfillResult(int gridFilled, int regionFilled, int regionFailed) {
    }

    /**
     * 등록한 빗물받이에 행정동 이름을 저장합니다. (조회는 트랜잭션 밖에서 미리 한다)
     *
     * @param drainId 빗물받이 id
     * @param regionName 행정동 이름 (null이면 아무것도 하지 않는다 — 다음 백필 때 다시)
     */
    @Transactional
    public void assignRegionName(Long drainId, String regionName) {
        if (regionName == null) {
            return;
        }
        drainRepository.findById(drainId).ifPresent(drain -> drain.assignRegionName(regionName));
    }

    /**
     * 격자 · 행정동 이름이 비어 있는 빗물받이를 채웁니다.
     *
     * @return 백필 결과
     */
    public BackfillResult backfill() {
        int grids = backfillGrids();
        int regionFilled = 0;
        int regionFailed = 0;
        if (drainRegionResolver.isEnabled()) {
            int[] region = backfillRegionNames();
            regionFilled = region[0];
            regionFailed = region[1];
        }
        return new BackfillResult(grids, regionFilled, regionFailed);
    }

    private int backfillGrids() {
        int filled = 0;
        while (true) {
            List<Object[]> rows = jdbcTemplate.query("SELECT id, latitude, longitude FROM drain "
                            + "WHERE grid_nx IS NULL AND deleted_at IS NULL ORDER BY id LIMIT ?",
                    (rs, n) -> new Object[] {rs.getLong("id"), rs.getDouble("latitude"),
                            rs.getDouble("longitude")},
                    BATCH_SIZE);
            if (rows.isEmpty()) {
                return filled;
            }
            List<Object[]> updates = new ArrayList<>();
            for (Object[] row : rows) {
                Grid grid = KmaGridConverter.toGrid((Double) row[1], (Double) row[2]);
                updates.add(new Object[] {grid.nx(), grid.ny(), row[0]});
            }
            jdbcTemplate.batchUpdate(
                    "UPDATE drain SET grid_nx = ?, grid_ny = ? WHERE id = ?", updates);
            filled += rows.size();
        }
    }

    /**
     * @return {채운 수, 실패 수}
     */
    private int[] backfillRegionNames() {
        int filled = 0;
        int failed = 0;
        long lastId = 0;
        while (true) {
            // 실패한 행을 이번 실행에서 다시 읽지 않도록 id 순으로 넘어간다
            List<Object[]> rows = jdbcTemplate.query("SELECT id, latitude, longitude FROM drain "
                            + "WHERE region_name IS NULL AND deleted_at IS NULL AND id > ? "
                            + "ORDER BY id LIMIT ?",
                    (rs, n) -> new Object[] {rs.getLong("id"), rs.getDouble("latitude"),
                            rs.getDouble("longitude")},
                    lastId, BATCH_SIZE);
            if (rows.isEmpty()) {
                return new int[] {filled, failed};
            }
            for (Object[] row : rows) {
                lastId = (Long) row[0];
                String name = drainRegionResolver.resolve((Double) row[1], (Double) row[2]);
                if (name == null) {
                    failed++;
                } else {
                    jdbcTemplate.update("UPDATE drain SET region_name = ? WHERE id = ?",
                            name, lastId);
                    filled++;
                }
                pause();
            }
        }
    }

    private static void pause() {
        try {
            Thread.sleep(REGION_CALL_INTERVAL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("행정동 백필이 중단됐습니다.", e);
        }
    }
}
