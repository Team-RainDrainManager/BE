package com.rainbutler.domain.forecast.client;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 기상청 API허브 동네예보 서비스(단기예보 · 초단기실황) 공통 호출기입니다.
 *
 * <ul>
 *   <li>파일럿 격자({@code rain-butler.kma.nx · ny}), 발표 시각, JSON, 인증키 파라미터를 붙여 GET 한다</li>
 *   <li>네트워크 · 5xx 실패는 2회 재시도, 4xx(인증 · 활용신청 문제)는 다시 불러도 같으므로 바로 실패</li>
 *   <li>인증키는 로그 · 예외 메시지에서 가린다</li>
 * </ul>
 */
@Slf4j
@Component
public class KmaRequestExecutor {

    private static final int MAX_RETRIES = 2;
    private static final long RETRY_WAIT_MILLIS = 2000;
    private static final DateTimeFormatter BASE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter BASE_TIME = DateTimeFormatter.ofPattern("HHmm");

    private final RestClient restClient;
    private final KmaProperties properties;

    public KmaRequestExecutor(
            @Qualifier("kmaRestClient") RestClient restClient, KmaProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    /**
     * 인증키가 설정돼 호출할 수 있는지 확인합니다.
     *
     * @return 설정 여부
     */
    public boolean isConfigured() {
        return properties.isConfigured();
    }

    /**
     * 발표 시각 기준으로 API를 호출합니다.
     *
     * @param url API 주소
     * @param base 발표 시각
     * @param numOfRows 받을 행 수
     * @param type 응답 타입
     * @return 응답 본문
     * @throws KmaApiException 호출 실패 · 4xx
     */
    public <T> T get(String url, LocalDateTime base, int numOfRows, Class<T> type) {
        for (int attempt = 0; ; attempt++) {
            try {
                return restClient.get()
                        .uri(url, builder -> builder
                                .queryParam("pageNo", 1)
                                .queryParam("numOfRows", numOfRows)
                                .queryParam("dataType", "JSON")
                                .queryParam("base_date", base.format(BASE_DATE))
                                .queryParam("base_time", base.format(BASE_TIME))
                                .queryParam("nx", properties.nx())
                                .queryParam("ny", properties.ny())
                                .queryParam("authKey", properties.authKey())
                                .build())
                        .retrieve()
                        .body(type);
            } catch (HttpClientErrorException e) {
                // 4xx(인증 · 활용신청 문제)는 다시 불러도 같으므로 재시도하지 않는다
                throw new KmaApiException("기상청 요청 거절 " + e.getStatusCode().value() + ": "
                        + mask(e.getResponseBodyAsString()));
            } catch (RestClientException e) {
                if (attempt >= MAX_RETRIES) {
                    throw new KmaApiException("기상청 호출 실패 (" + (attempt + 1) + "회): "
                            + mask(e.getMessage()));
                }
                log.warn("[기상청] 호출 실패, 재시도 {}/{}: {}",
                        attempt + 1, MAX_RETRIES, mask(e.getMessage()));
                sleep();
            }
        }
    }

    /**
     * 기상청 응답 헤더를 확인합니다. API허브 오류 · 형식 오류 · 결과 코드가 정상(00)이 아니면 예외입니다.
     *
     * @param result API허브 오류 (없으면 null)
     * @param header 응답 헤더
     * @throws KmaApiException 정상 응답이 아님
     */
    public static void requireSuccess(
            KmaForecastResponse.HubResult result, KmaForecastResponse.Header header) {
        if (result != null) {
            throw new KmaApiException("기상청 API허브 오류 " + result.status() + ": "
                    + result.message());
        }
        if (header == null) {
            throw new KmaApiException("기상청 응답 형식이 다릅니다 (header 없음).");
        }
        if (!"00".equals(header.resultCode())) {
            throw new KmaApiException("기상청 오류 응답 " + header.resultCode() + ": "
                    + header.resultMsg());
        }
    }

    /**
     * 메시지에 인증키가 섞여 있으면 가립니다. (요청 URL이 예외 메시지에 들어가는 경우)
     */
    private String mask(String message) {
        if (message == null) {
            return "";
        }
        return properties.isConfigured() ? message.replace(properties.authKey(), "****") : message;
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
