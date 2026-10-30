package com.rainbutler.domain.push.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.rainbutler.domain.user.entity.User;
import com.rainbutler.global.config.PolicyProperties;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

/**
 * 알림 수신 · 야간 푸시 동의에 따른 발송 가능 여부를 검증합니다.
 */
class PushConsentPolicyTest {

    private final PushConsentPolicy policy = new PushConsentPolicy(new PolicyProperties(
            3, 14, 28, 28, 35, 300, 3, 50, new BigDecimal("0.7"),
            LocalTime.of(6, 0), LocalTime.of(20, 0), 90, LocalTime.of(22, 0), LocalTime.of(8, 0),
            7, 14, 3, 14, 15,
            LocalTime.of(20, 0), 60, new BigDecimal("5"), 3, 1, 1, LocalTime.of(21, 0),
            LocalTime.of(9, 0)));

    @Test
    void 알림_수신에_동의하지_않으면_낮에도_보내지_않는다() {
        assertThat(policy.allows(user(false, true), LocalTime.of(14, 0))).isFalse();
    }

    @Test
    void 알림_수신에_동의했으면_낮에는_야간_동의와_관계없이_보낸다() {
        assertThat(policy.allows(user(true, false), LocalTime.of(14, 0))).isTrue();
        assertThat(policy.allows(user(true, true), LocalTime.of(14, 0))).isTrue();
    }

    @Test
    void 야간에는_야간_푸시에_동의한_사용자에게만_보낸다() {
        assertThat(policy.allows(user(true, false), LocalTime.of(23, 0))).isFalse();
        assertThat(policy.allows(user(true, false), LocalTime.of(3, 0))).isFalse();
        assertThat(policy.allows(user(true, true), LocalTime.of(23, 0))).isTrue();
    }

    @Test
    void 야간은_22시부터_08시_전까지다() {
        assertThat(policy.isNightTime(LocalTime.of(21, 59))).isFalse();
        assertThat(policy.isNightTime(LocalTime.of(22, 0))).isTrue();
        assertThat(policy.isNightTime(LocalTime.of(0, 0))).isTrue();
        assertThat(policy.isNightTime(LocalTime.of(7, 59))).isTrue();
        assertThat(policy.isNightTime(LocalTime.of(8, 0))).isFalse();
    }

    private static User user(boolean notificationEnabled, boolean nightPushConsent) {
        User user = User.local("a@test.com", "{noop}x", "집사");
        user.completeOnboarding(LocalDateTime.now(), notificationEnabled, nightPushConsent);
        return user;
    }
}
