package com.rainbutler.domain.auth.service;

import com.rainbutler.domain.auth.dto.TokenResponse;
import com.rainbutler.domain.auth.entity.RefreshToken;
import com.rainbutler.domain.auth.repository.RefreshTokenRepository;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.security.JwtProperties;
import com.rainbutler.global.security.JwtTokenProvider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * access · refresh 토큰을 발급 · 회전 · 폐기합니다.
 *
 * <p>refresh 토큰은 32바이트 랜덤 문자열이고, DB에는 SHA-256 해시만 저장합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private static final int REFRESH_TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * 사용자에게 access · refresh 토큰을 새로 발급합니다.
     *
     * @param user 사용자
     * @return 발급한 토큰
     */
    @Transactional
    public TokenResponse issue(User user) {
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getRole());
        String refreshToken = generateRefreshToken();
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(hash(refreshToken))
                .expiresAt(LocalDateTime.now().plus(jwtProperties.refreshTokenValidity()))
                .build());
        return TokenResponse.bearer(
                accessToken, refreshToken, jwtTokenProvider.accessTokenValiditySeconds());
    }

    /**
     * refresh 토큰을 회전합니다. 기존 토큰을 폐기하고 새 토큰을 발급합니다.
     *
     * <p>이미 폐기된 토큰이 다시 오면 탈취로 보고 해당 사용자의 refresh 토큰을 전부 폐기합니다.
     * 이 폐기는 예외를 던져도 롤백되지 않아야 하므로 {@code noRollbackFor}를 둡니다.
     *
     * @param refreshToken 클라이언트가 보낸 refresh 토큰
     * @return 새로 발급한 토큰
     * @throws BusinessException 없음 · 만료 · 폐기된 토큰 (INVALID_REFRESH_TOKEN)
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public TokenResponse rotate(String refreshToken) {
        LocalDateTime now = LocalDateTime.now();
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash(refreshToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        if (token.isRevoked()) {
            Long userId = token.getUser().getId();
            int revoked = refreshTokenRepository.revokeAllByUserId(userId, now);
            log.warn("[refresh 재사용 감지] userId={} 의 refresh 토큰 {}개 폐기", userId, revoked);
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        if (token.isExpired(now)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        token.revoke();
        return issue(token.getUser());
    }

    /**
     * 로그아웃: 본인의 refresh 토큰만 폐기합니다.
     *
     * <p>없거나 다른 사용자의 토큰이면 아무것도 하지 않습니다. (로그아웃은 항상 성공 응답)
     *
     * @param userId 로그인 사용자 id
     * @param refreshToken 폐기할 refresh 토큰
     */
    @Transactional
    public void revoke(Long userId, String refreshToken) {
        refreshTokenRepository.findByTokenHash(hash(refreshToken))
                .filter(token -> token.getUser().getId().equals(userId))
                .ifPresent(RefreshToken::revoke);
    }

    /**
     * URL에 그대로 쓸 수 있는 랜덤 refresh 토큰을 만듭니다.
     */
    private String generateRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * refresh 토큰의 SHA-256 해시(hex 64자)를 구합니다.
     */
    static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // 모든 JVM이 SHA-256을 지원하므로 발생하지 않는다
            throw new IllegalStateException(e);
        }
    }
}
