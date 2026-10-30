package com.rainbutler.domain.forecast.client;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 기상청 API 호출용 RestClient 설정입니다. (연결 타임아웃 5초, 읽기 타임아웃 10초)
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
}
