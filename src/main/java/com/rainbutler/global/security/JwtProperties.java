package com.rainbutler.global.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 설정입니다. ({@code rain-butler.jwt})
 *
 * @param secret HS256 서명 키 (Base64 인코딩, 디코딩 후 32바이트 이상)
 * @param issuer 발급자 ({@code iss} 클레임)
 * @param accessTokenValidity access 토큰 유효 기간
 * @param refreshTokenValidity refresh 토큰 유효 기간
 */
@ConfigurationProperties(prefix = "rain-butler.jwt")
public record JwtProperties(
        String secret,
        String issuer,
        Duration accessTokenValidity,
        Duration refreshTokenValidity) {
}
