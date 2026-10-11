package com.rainbutler.global.security;

import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * JWT 서명 키 · 인코더 · 디코더 설정입니다. (HS256)
 *
 * <p>{@code JWT_SECRET}이 없거나 너무 짧으면 앱 시작을 실패시킵니다.
 */
@Configuration
public class JwtConfig {

    /** HS256에 필요한 최소 키 길이 (바이트) */
    private static final int MIN_SECRET_BYTES = 32;

    /**
     * Base64로 인코딩된 비밀값에서 HMAC 서명 키를 만듭니다.
     *
     * @param properties JWT 설정
     * @return 서명 키
     */
    @Bean
    public SecretKey jwtSecretKey(JwtProperties properties) {
        String secret = properties.secret();
        // 환경변수가 없으면 "${JWT_SECRET}" 문자열이 그대로 들어올 수 있어 함께 막는다
        if (secret == null || secret.isBlank() || secret.startsWith("${")) {
            throw new IllegalStateException(
                    "JWT_SECRET이 설정되지 않았습니다. openssl rand -base64 32 로 생성해 환경변수(.env)에 넣으세요.");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(secret.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("JWT_SECRET은 Base64 인코딩 값이어야 합니다.", e);
        }
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET은 디코딩 후 " + MIN_SECRET_BYTES
                    + "바이트 이상이어야 합니다. (현재 " + keyBytes.length + "바이트)");
        }
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    /**
     * access 토큰 서명용 인코더입니다.
     */
    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return NimbusJwtEncoder.withSecretKey(jwtSecretKey).algorithm(MacAlgorithm.HS256).build();
    }

    /**
     * access 토큰 검증용 디코더입니다. 서명 · 만료 · issuer를 검사합니다.
     */
    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey, JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }

    /**
     * {@code role} 클레임을 {@code ROLE_} 권한으로, {@code sub}를 principal 이름으로 변환합니다.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName(JwtTokenProvider.ROLE_CLAIM);
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        converter.setPrincipalClaimName("sub");
        return converter;
    }
}
