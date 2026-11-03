package com.rainbutler.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 빗물받이 주소 조회 설정입니다. ({@code rain-butler.address})
 *
 * @param reverseGeocodingEnabled 좌표로 주소를 찾을지 여부. 테스트 · 외부 API 장애 시 끈다.
 */
@ConfigurationProperties(prefix = "rain-butler.address")
public record AddressProperties(boolean reverseGeocodingEnabled) {
}
