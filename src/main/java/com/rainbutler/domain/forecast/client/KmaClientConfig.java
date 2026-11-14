package com.rainbutler.domain.forecast.client;

import com.rainbutler.domain.weather.client.KmaObservationClient;
import com.rainbutler.domain.weather.client.WeatherOnDemandProperties;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

/**
 * 기상청 API 호출용 RestClient 설정입니다.
 *
 * <ul>
 *   <li>수집 배치: 연결 타임아웃 5초, 읽기 타임아웃 10초, 재시도 2회</li>
 *   <li>배너 조회 시 호출: 타임아웃 {@code rain-butler.kma.on-demand.fetch-timeout}(기본 10초), 재시도 없음.
 *       뒤에서 끝까지 받으므로 응답 대기(2초)와 별개다</li>
 * </ul>
 */
@Configuration
public class KmaClientConfig {

    /** 배너 조회 시 기상청을 부르는 스레드 수 */
    private static final int ON_DEMAND_THREADS = 3;
    /** 배너 조회 시 호출 대기열 상한 (넘치면 부르지 않음) */
    private static final int ON_DEMAND_QUEUE = 50;

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
     * 배너 조회 시 초단기실황을 받아 오는 클라이언트입니다. 백그라운드 스레드에서 쓰고 재시도는 없습니다.
     * (수집 배치는 {@code @Primary}인 기본 {@link KmaObservationClient}를 쓴다)
     */
    @Bean
    public KmaObservationClient kmaOnDemandObservationClient(KmaProperties properties,
            WeatherOnDemandProperties onDemand) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(onDemand.fetchTimeout());
        requestFactory.setReadTimeout(onDemand.fetchTimeout());
        RestClient restClient = RestClient.builder().requestFactory(requestFactory).build();
        return new KmaObservationClient(
                new KmaRequestExecutor(restClient, properties, 0), properties);
    }

    /**
     * 배너 조회 시 기상청 호출을 뒤에서 처리하는 스레드 풀입니다. (스레드 3개, 대기 50건)
     *
     * <p>대기열이 가득 차면 호출하지 않고(분당 상한과 같은 처리) 저장된 값으로 응답한다. 종료 시 진행 중 호출을 최대
     * 10초 기다린다.
     */
    @Bean
    public ThreadPoolTaskExecutor weatherOnDemandExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(ON_DEMAND_THREADS);
        executor.setMaxPoolSize(ON_DEMAND_THREADS);
        executor.setQueueCapacity(ON_DEMAND_QUEUE);
        executor.setThreadNamePrefix("weather-on-demand-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
