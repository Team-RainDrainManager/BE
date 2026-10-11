package com.rainbutler.domain.auth.client;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 카카오 API 호출용 RestClient 설정입니다. (연결 · 읽기 타임아웃 5초)
 */
@Configuration
public class KakaoClientConfig {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    /**
     * 카카오 API 전용 RestClient를 만듭니다.
     */
    @Bean
    public RestClient kakaoRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT);
        requestFactory.setReadTimeout(TIMEOUT);
        return RestClient.builder().requestFactory(requestFactory).build();
    }
}
