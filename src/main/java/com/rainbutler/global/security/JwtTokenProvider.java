package com.rainbutler.global.security;

import com.rainbutler.domain.user.entity.Role;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * access 토큰(JWT)을 발급합니다.
 *
 * <p>클레임: {@code sub}=userId, {@code role}=Role 이름, {@code iss}, {@code iat}, {@code exp}
 */
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    /** 역할 클레임 이름 */
    public static final String ROLE_CLAIM = "role";

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;

    /**
     * 지금 시각 기준으로 access 토큰을 발급합니다.
     *
     * @param userId 사용자 id
     * @param role 사용자 역할
     * @return access 토큰
     */
    public String createAccessToken(Long userId, Role role) {
        return createAccessToken(userId, role, Instant.now());
    }

    /**
     * 지정한 발급 시각 기준으로 access 토큰을 발급합니다. (테스트에서 만료 토큰을 만들 때 사용)
     *
     * @param userId 사용자 id
     * @param role 사용자 역할
     * @param issuedAt 발급 시각
     * @return access 토큰
     */
    public String createAccessToken(Long userId, Role role, Instant issuedAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(String.valueOf(userId))
                .claim(ROLE_CLAIM, role.name())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(properties.accessTokenValidity()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /**
     * access 토큰 유효 기간(초)입니다. 응답의 {@code accessTokenExpiresIn}에 씁니다.
     *
     * @return 유효 기간 (초)
     */
    public long accessTokenValiditySeconds() {
        return properties.accessTokenValidity().toSeconds();
    }
}
