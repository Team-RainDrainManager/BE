package com.rainbutler.domain.push;

import static org.assertj.core.api.Assertions.assertThat;

import com.rainbutler.domain.push.entity.FcmPlatform;
import com.rainbutler.domain.push.entity.FcmToken;
import com.rainbutler.domain.push.repository.FcmTokenRepository;
import com.rainbutler.domain.push.service.FcmTokenService;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.support.IntegrationTestSupport;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * FCM 토큰 등록 · 삭제를 실제 DB로 검증합니다.
 */
class FcmTokenServiceTest extends IntegrationTestSupport {

    @Autowired
    private FcmTokenService fcmTokenService;

    @Autowired
    private FcmTokenRepository fcmTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 처음_보는_토큰은_새로_등록한다() {
        User user = saveUser();
        String token = uniqueToken();

        fcmTokenService.register(user.getId(), token, FcmPlatform.WEB);

        assertThat(ownerOf(token)).contains(user.getId());
        assertThat(fcmTokenRepository.findAllByUser_Id(user.getId())).hasSize(1);
    }

    @Test
    void 같은_토큰을_다른_사용자가_등록하면_그_사용자로_옮긴다() {
        User first = saveUser();
        User second = saveUser();
        String token = uniqueToken();

        fcmTokenService.register(first.getId(), token, FcmPlatform.WEB);
        fcmTokenService.register(second.getId(), token, FcmPlatform.ANDROID);

        assertThat(ownerOf(token)).contains(second.getId());
        assertThat(fcmTokenRepository.findAllByUser_Id(first.getId())).isEmpty();
        assertThat(fcmTokenRepository.findByToken(token)).map(FcmToken::getPlatform)
                .contains(FcmPlatform.ANDROID);
    }

    @Test
    void 내_토큰을_삭제하면_행이_사라진다() {
        User user = saveUser();
        String token = uniqueToken();
        fcmTokenService.register(user.getId(), token, FcmPlatform.IOS);

        fcmTokenService.delete(user.getId(), token);

        // 소프트 삭제가 아니라 행 자체가 없어야 한다 (같은 토큰 재등록 가능)
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM fcm_token WHERE token = ?", Integer.class, token);
        assertThat(rows).isZero();
        fcmTokenService.register(user.getId(), token, FcmPlatform.IOS);
        assertThat(ownerOf(token)).contains(user.getId());
    }

    @Test
    void 남의_토큰_삭제_요청은_무시한다() {
        User owner = saveUser();
        User other = saveUser();
        String token = uniqueToken();
        fcmTokenService.register(owner.getId(), token, FcmPlatform.WEB);

        fcmTokenService.delete(other.getId(), token);
        fcmTokenService.delete(other.getId(), "not-exists-token");

        assertThat(ownerOf(token)).contains(owner.getId());
    }

    private Optional<Long> ownerOf(String token) {
        return jdbcTemplate.queryForList(
                "SELECT user_id FROM fcm_token WHERE token = ?", Long.class, token)
                .stream().findFirst();
    }

    private User saveUser() {
        return userRepository.save(User.local(
                UUID.randomUUID() + "@test.com", "{noop}irrelevant", "푸시집사"));
    }

    private static String uniqueToken() {
        return "fcm-" + UUID.randomUUID();
    }
}
