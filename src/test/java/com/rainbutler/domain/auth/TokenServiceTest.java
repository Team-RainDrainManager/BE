package com.rainbutler.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rainbutler.domain.auth.dto.TokenResponse;
import com.rainbutler.domain.auth.service.TokenService;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.support.IntegrationTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * 토큰 발급 · 회전 · 재사용 감지를 검증합니다.
 *
 * <p>재사용 감지 때 "전체 폐기"가 실제로 커밋되는지 보려고 테스트 트랜잭션을 쓰지 않습니다.
 */
class TokenServiceTest extends IntegrationTestSupport {

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 발급한_access_토큰에_sub와_role이_들어간다() {
        User user = saveUser();

        TokenResponse token = tokenService.issue(user);
        Jwt jwt = jwtDecoder.decode(token.accessToken());

        assertThat(jwt.getSubject()).isEqualTo(String.valueOf(user.getId()));
        assertThat(jwt.getClaimAsString("role")).isEqualTo("USER");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("rain-butler");
        assertThat(token.tokenType()).isEqualTo("Bearer");
        assertThat(token.accessTokenExpiresIn()).isEqualTo(1800);
    }

    @Test
    void 회전_후_이전_refresh를_다시_쓰면_사용자의_refresh가_전부_폐기된다() {
        User user = saveUser();
        TokenResponse first = tokenService.issue(user);
        TokenResponse second = tokenService.rotate(first.refreshToken());

        // 이미 폐기된 첫 토큰을 다시 보내면 탈취로 본다
        assertThatThrownBy(() -> tokenService.rotate(first.refreshToken()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);

        // 정상이던 두 번째 토큰도 함께 폐기됐어야 한다
        assertThatThrownBy(() -> tokenService.rotate(second.refreshToken()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
        Integer alive = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refresh_token WHERE user_id = ? AND revoked_at IS NULL",
                Integer.class, user.getId());
        assertThat(alive).isZero();
    }

    @Test
    void 없는_refresh_토큰은_거부한다() {
        assertThatThrownBy(() -> tokenService.rotate("not-exists"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    private User saveUser() {
        return userRepository.save(User.local(
                UUID.randomUUID() + "@test.com", "{noop}irrelevant", "테스트집사"));
    }
}
