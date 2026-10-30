package com.rainbutler.global.scheduling;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 배치 스케줄러 설정입니다. ({@code rain-butler.scheduling})
 *
 * @param enabled false면 {@code @Scheduled} 배치가 아무 일도 하지 않고 바로 끝난다 (테스트 프로필)
 */
@ConfigurationProperties(prefix = "rain-butler.scheduling")
public record SchedulingProperties(boolean enabled) {
}
