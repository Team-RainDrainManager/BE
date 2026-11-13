package com.rainbutler.domain.drain.service;

import com.rainbutler.domain.drain.service.DrainAddressResolver.AddressLookupForbiddenException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 주소가 비어 있는 빗물받이의 주소를 채웁니다. (기존 데이터 백필)
 *
 * <p>주소는 등록할 때 한 번 역지오코딩해 저장합니다. 그 전에 등록됐거나 주소 조회가 실패했던 행은 비어 있으므로,
 * 서버가 뜰 때 한 번씩 채웁니다.
 *
 * <ul>
 *   <li>카카오 호출이라 초당 호출 수를 제한하고, 실패한 행은 비워 두었다가 다음 시작 때 다시 한다</li>
 *   <li>카카오가 403(카카오맵 사용 설정 꺼짐)이면 남은 행은 부르지 않고 멈춘다</li>
 *   <li>비어 있는 행만 채우므로 여러 번 돌려도 같다</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DrainAddressBackfillService {

    /** 한 번에 읽는 행 수 */
    static final int BATCH_SIZE = 50;
    /** 카카오 호출 사이 간격 (초당 10회 이하) */
    private static final long CALL_INTERVAL_MILLIS = 100;

    private final DrainAddressResolver drainAddressResolver;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 백필 결과입니다.
     *
     * @param filled 주소를 채운 행 수
     * @param failed 주소를 찾지 못한 행 수 (다음 시작 때 다시)
     */
    public record BackfillResult(int filled, int failed) {
    }

    /**
     * 주소가 비어 있는 빗물받이를 채웁니다.
     *
     * @return 백필 결과
     */
    public BackfillResult backfill() {
        if (!drainAddressResolver.isEnabled()) {
            return new BackfillResult(0, 0);
        }

        int filled = 0;
        int failed = 0;
        long lastId = 0;
        while (true) {
            // 실패한 행을 이번 실행에서 다시 읽지 않도록 id 순으로 넘어간다
            List<Object[]> rows = jdbcTemplate.query("SELECT id, latitude, longitude FROM drain "
                            + "WHERE (address IS NULL OR address = '') AND deleted_at IS NULL "
                            + "AND id > ? ORDER BY id LIMIT ?",
                    (rs, n) -> new Object[] {rs.getLong("id"), rs.getDouble("latitude"),
                            rs.getDouble("longitude")},
                    lastId, BATCH_SIZE);
            if (rows.isEmpty()) {
                return new BackfillResult(filled, failed);
            }
            for (Object[] row : rows) {
                lastId = (Long) row[0];
                String address;
                try {
                    address = drainAddressResolver.resolveForBackfill(
                            (Double) row[1], (Double) row[2]);
                } catch (AddressLookupForbiddenException e) {
                    log.warn("[빗물받이 주소 백필] 카카오가 주소 조회를 거절해(403) 주소 백필을 멈춥니다.");
                    return new BackfillResult(filled, failed + 1);
                }
                if (address == null) {
                    failed++;
                } else {
                    jdbcTemplate.update("UPDATE drain SET address = ? WHERE id = ?",
                            address, lastId);
                    filled++;
                }
                pause();
            }
        }
    }

    private static void pause() {
        try {
            Thread.sleep(CALL_INTERVAL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("주소 백필이 중단됐습니다.", e);
        }
    }
}
