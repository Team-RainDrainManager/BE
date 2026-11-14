package com.rainbutler.domain.forecast.client;

import com.rainbutler.domain.weather.client.KmaObservationClient;
import com.rainbutler.domain.weather.client.WeatherOnDemandProperties;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 기상청 API 호출용 RestClient 설정입니다.
 *
 * <ul>
 *   <li>수집 배치: 연결 타임아웃 5초, 읽기 타임아웃 10초, 재시도 2회</li>
 *   <li>배너 조회 시 호출: 타임아웃 {@code rain-butler.kma.on-demand.timeout}(기본 2초), 재시도 없음</li>
 * </ul>
 */
@Configuration
public class KmaClientConfig {

    /**
     * 기상청 API 전용 RestClient를 만듭니다.
     */
    @Bean
    public RestClient kmaRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    /**
     * 배너 조회 시 초단기실황을 바로 받아 오는 클라이언트입니다. 지도 응답을 늦추지 않게 짧은 타임아웃 · 재시도 없음.
     * (수집 배치는 {@code @Primary}인 기본 {@link KmaObservationClient}를 쓴다)
     */
    @Bean
    public KmaObservationClient kmaOnDemandObservationClient(KmaProperties properties,
            WeatherOnDemandProperties onDemand) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(onDemand.timeout());
        requestFactory.setReadTimeout(onDemand.timeout());
        RestClient restClient = RestClient.builder().requestFactory(requestFactory).build();
        return new KmaObservationClient(
                new KmaRequestExecutor(restClient, properties, 0), properties);
    }
}
