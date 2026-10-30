package com.rainbutler.domain.forecast.client;

import com.rainbutler.domain.forecast.client.KmaForecastResponse.Item;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 기상청 단기예보(getVilageFcst) 클라이언트입니다.
 *
 * <p>파일럿 격자 한 곳({@code rain-butler.kma.nx · ny})의 가장 최근 발표 예보를 받아 날짜별 {@link DailyRain}으로
 * 돌려줍니다. 인증키가 없으면 호출하지 않고 빈 목록을 돌려줍니다 (CI · 로컬 키 없는 환경).
 *
 * <p>인증키는 로그 · 예외 메시지에 남기지 않습니다.
 */
@Slf4j
@Component
public class KmaForecastClient {

    /** 한 번에 받을 행 수. 02 · 05시 발표는 사흘치 이상이라 1000행을 넘을 수 있어 넉넉히 둔다 */
    private static final int NUM_OF_ROWS = 2000;
    private static final int MAX_RETRIES = 2;
    private static final long RETRY_WAIT_MILLIS = 2000;
    private static final String SUCCESS = "00";
    private static final DateTimeFormatter BASE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter BASE_TIME = DateTimeFormatter.ofPattern("HHmm");

    private final RestClient restClient;
    private final KmaProperties properties;
    private final AtomicBoolean missingKeyWarned = new AtomicBoolean(false);

    public KmaForecastClient(
            @Qualifier("kmaRestClient") RestClient restClient, KmaProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    /**
     * 가장 최근 발표 예보를 날짜별로 요약해 가져옵니다.
     *
     * @param now 현재 시각 (발표 시각 계산용)
     * @param popThreshold 비 시작으로 보는 강수확률 기준 (%)
     * @return 날짜순 하루 요약 (인증키가 없으면 빈 목록)
     * @throws KmaApiException 호출 실패 · 오류 응답
     */
    public List<DailyRain> fetchDailyRain(LocalDateTime now, int popThreshold) {
        if (!properties.isConfigured()) {
            if (missingKeyWarned.compareAndSet(false, true)) {
                log.warn("기상청 인증키(KMA_AUTH_KEY)가 설정되지 않아 예보 수집을 건너뜁니다.");
            }
            return List.of();
        }
        LocalDateTime base = KmaBaseTime.latest(now);
        KmaForecastResponse response = callWithRetry(base);
        List<Item> items = extractItems(response);
        log.info("[기상청 예보] 발표 {} 항목 {}개", base, items.size());
        return DailyRainAggregator.aggregate(items, popThreshold);
    }

    private KmaForecastResponse callWithRetry(LocalDateTime base) {
        for (int attempt = 0; ; attempt++) {
            try {
                return restClient.get()
                        .uri(properties.baseUrl(), builder -> builder
                                .queryParam("pageNo", 1)
                                .queryParam("numOfRows", NUM_OF_ROWS)
                                .queryParam("dataType", "JSON")
                                .queryParam("base_date", base.format(BASE_DATE))
                                .queryParam("base_time", base.format(BASE_TIME))
                                .queryParam("nx", properties.nx())
                                .queryParam("ny", properties.ny())
                                .queryParam("authKey", properties.authKey())
                                .build())
                        .retrieve()
                        .body(KmaForecastResponse.class);
            } catch (HttpClientErrorException e) {
                // 4xx(인증 · 활용신청 문제)는 다시 불러도 같으므로 재시도하지 않는다
                throw new KmaApiException("기상청 요청 거절 " + e.getStatusCode().value() + ": "
                        + mask(e.getResponseBodyAsString()));
            } catch (RestClientException e) {
                if (attempt >= MAX_RETRIES) {
                    throw new KmaApiException("기상청 호출 실패 (" + (attempt + 1) + "회): "
                            + mask(e.getMessage()));
                }
                log.warn("[기상청 예보] 호출 실패, 재시도 {}/{}: {}",
                        attempt + 1, MAX_RETRIES, mask(e.getMessage()));
                sleep();
            }
        }
    }

    private List<Item> extractItems(KmaForecastResponse response) {
        if (response == null) {
            throw new KmaApiException("기상청 응답이 비어 있습니다.");
        }
        if (response.result() != null) {
            throw new KmaApiException("기상청 API허브 오류 " + response.result().status() + ": "
                    + response.result().message());
        }
        KmaForecastResponse.Header header =
                response.response() == null ? null : response.response().header();
        if (header == null) {
            throw new KmaApiException("기상청 응답 형식이 다릅니다 (header 없음).");
        }
        if (!SUCCESS.equals(header.resultCode())) {
            throw new KmaApiException("기상청 오류 응답 " + header.resultCode() + ": "
                    + header.resultMsg());
        }
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

    /**
     * 메시지에 인증키가 섞여 있으면 가립니다. (요청 URL이 예외 메시지에 들어가는 경우)
     */
    private String mask(String message) {
        if (message == null) {
            return "";
        }
        return message.replace(properties.authKey(), "****");
    }

    private void sleep() {
        try {
            Thread.sleep(RETRY_WAIT_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KmaApiException("기상청 재시도 대기 중 중단됐습니다.");
        }
    }
}
