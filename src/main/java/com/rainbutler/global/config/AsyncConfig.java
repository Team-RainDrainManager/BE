package com.rainbutler.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * {@code @Async} 비동기 실행 설정입니다. (푸시 발송 등)
 *
 * <p>실행기는 Spring Boot가 자동으로 만드는 {@code applicationTaskExecutor}를 씁니다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
