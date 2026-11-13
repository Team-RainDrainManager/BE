package com.rainbutler.domain.forecast.client;

import com.rainbutler.domain.forecast.client.KmaForecastResponse.Item;
import com.rainbutler.global.geo.KmaGridConverter.Grid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 기상청 단기예보(getVilageFcst) 클라이언트입니다.
 *
 * <p>기상청 격자 한 곳의 가장 최근 발표 예보를 받아 날짜별 {@link DailyRain}으로
 * 돌려줍니다. 인증키가 없으면 호출하지 않고 빈 목록을 돌려줍니다 (CI · 로컬 키 없는 환경).
 *
 * <p>호출 · 재시도 · 인증키 가리기는 {@link KmaRequestExecutor}가 합니다.
 */
@Slf4j
@Component
public class KmaForecastClient {

    /** 한 번에 받을 행 수. 02 · 05시 발표는 사흘치 이상이라 1000행을 넘을 수 있어 넉넉히 둔다 */
    private static final int NUM_OF_ROWS = 2000;

    private final KmaRequestExecutor executor;
    private final KmaProperties properties;
    private final AtomicBoolean missingKeyWarned = new AtomicBoolean(false);

    public KmaForecastClient(KmaRequestExecutor executor, KmaProperties properties) {
        this.executor = executor;
        this.properties = properties;
    }

    /**
     * 가장 최근 발표 예보를 날짜별로 요약해 가져옵니다.
     *
     * @param grid 기상청 격자
     * @param now 현재 시각 (발표 시각 계산용)
     * @param popThreshold 비 시작으로 보는 강수확률 기준 (%)
     * @return 날짜순 하루 요약 (인증키가 없으면 빈 목록)
     * @throws KmaApiException 호출 실패 · 오류 응답
     */
    public List<DailyRain> fetchDailyRain(Grid grid, LocalDateTime now, int popThreshold) {
        if (!properties.isConfigured()) {
            if (missingKeyWarned.compareAndSet(false, true)) {
                log.warn("기상청 인증키(KMA_AUTH_KEY)가 설정되지 않아 예보 수집을 건너뜁니다.");
            }
            return List.of();
        }
        LocalDateTime base = KmaBaseTime.latest(now);
        KmaForecastResponse response = executor.get(
                properties.baseUrl(), grid, base, NUM_OF_ROWS, KmaForecastResponse.class);
        List<Item> items = extractItems(response);
        log.info("[기상청 예보] 격자 {},{} 발표 {} 항목 {}개", grid.nx(), grid.ny(), base,
                items.size());
        return DailyRainAggregator.aggregate(items, popThreshold);
    }

    private List<Item> extractItems(KmaForecastResponse response) {
        if (response == null) {
            throw new KmaApiException("기상청 응답이 비어 있습니다.");
        }
        KmaRequestExecutor.requireSuccess(response.result(),
                response.response() == null ? null : response.response().header());
        KmaForecastResponse.Body body = response.response().body();
        List<Item> items = Optional.ofNullable(body)
                .map(KmaForecastResponse.Body::items)
                .map(KmaForecastResponse.Items::item)
                .orElse(List.of());
        if (body != null && body.totalCount() != null && body.totalCount() > items.size()) {
            log.warn("[기상청 예보] 전체 {}개 중 {}개만 받았습니다 (numOfRows 부족)",
                    body.totalCount(), items.size());
        }
        return items;
    }
}
