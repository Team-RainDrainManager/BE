package com.rainbutler.global.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 현재 시각을 주입받아 쓰기 위한 Clock 설정입니다. 테스트에서 시각을 고정할 수 있습니다.
 *
 * <p>기본 시간대는 앱 시작 시 Asia/Seoul로 고정돼 있습니다.
 */
@Configuration
public class ClockConfig {

    /**
     * 시스템 기본 시간대(한국 시간)의 Clock입니다.
     */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
