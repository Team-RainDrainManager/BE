package com.rainbutler.domain.drain.ai;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Gemini API 호출용 RestClient 설정입니다. (연결 타임아웃 5초, 읽기 타임아웃 60초)
 *
 * <p>사진과 함께 보내면 답이 3~6초 걸리므로 읽기는 넉넉하게 둡니다.
 */
@Configuration
public class AiClientConfig {

    /**
     * Gemini API 전용 RestClient를 만듭니다.
     */
    @Bean
    public RestClient geminiRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(60));
        return RestClient.builder().requestFactory(requestFactory).build();
    }
}
