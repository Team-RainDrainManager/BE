package com.rainbutler.global.logging;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.task.TaskDecorator;

/**
 * 요청 로그 설정입니다.
 */
@Configuration
public class LoggingConfig {

    /**
     * 요청 ID · 접근 로그 필터를 보안 필터보다 앞에 등록합니다. (인증 실패 요청도 요청 ID · 접근 로그가 남게)
     */
    @Bean
    public FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilter() {
        FilterRegistrationBean<RequestLoggingFilter> registration =
                new FilterRegistrationBean<>(new RequestLoggingFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    /**
     * {@code @Async} 스레드(푸시 발송 등)에도 요청 ID · 사용자 id가 이어지게 MDC를 복사합니다.
     * Boot가 자동 구성하는 비동기 실행기가 이 빈을 씁니다.
     */
    @Bean
    public TaskDecorator mdcTaskDecorator() {
        return runnable -> {
            Map<String, String> context = MDC.getCopyOfContextMap();
            return () -> {
                Map<String, String> previous = MDC.getCopyOfContextMap();
                if (context == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(context);
                }
                try {
                    runnable.run();
                } finally {
                    if (previous == null) {
                        MDC.clear();
                    } else {
                        MDC.setContextMap(previous);
                    }
                }
            };
        };
    }
}
