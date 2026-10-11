package com.rainbutler.global.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS 설정입니다. ({@code rain-butler.cors})
 *
 * @param allowedOrigins 허용할 프론트엔드 origin 목록 (예: http://localhost:5173)
 */
@ConfigurationProperties(prefix = "rain-butler.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
