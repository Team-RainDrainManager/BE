package com.rainbutler.domain.adoption.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.rainbutler.support.TestPolicies;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * 정기 점검 주기(입양일 기준 14일 고정)와 다음 관리 예정일 계산을 검증합니다.
 */
class ManagementCyclePolicyTest {

    private static final LocalDateTime ADOPTED_AT = LocalDateTime.of(2026, 11, 1, 10, 30);

    private final ManagementCyclePolicy policy =
            new ManagementCyclePolicy(TestPolicies.defaults());

    @Test
    void 주기는_계절과_관계없이_14일이다() {
        assertThat(policy.cycleDays()).isEqualTo(14);
    }

    @Test
    void 입양일_기준으로_주기가_고정된다() {
        assertThat(policy.cycleStart(ADOPTED_AT, ADOPTED_AT.plusDays(3)))
                .isEqualTo(ADOPTED_AT);
        assertThat(policy.cycleStart(ADOPTED_AT, ADOPTED_AT.plusDays(14)))
                .isEqualTo(ADOPTED_AT.plusDays(14));
        assertThat(policy.cycleStart(ADOPTED_AT, ADOPTED_AT.plusDays(27)))
                .isEqualTo(ADOPTED_AT.plusDays(14));
        assertThat(policy.cycleStart(ADOPTED_AT, ADOPTED_AT.plusDays(28)))
                .isEqualTo(ADOPTED_AT.plusDays(28));
    }

    @Test
    void 다음_관리_예정일은_주기_끝이라_점검일과_무관하다() {
        // 입양 3일째에 점검해도 다음 예정일은 입양 14일째다
        assertThat(policy.nextManagementAt(ADOPTED_AT, ADOPTED_AT.plusDays(3)))
                .isEqualTo(ADOPTED_AT.plusDays(14));
        // 주기 마지막 날에 점검하면 다음 예정일은 그다음 주기 끝이다
        assertThat(policy.nextManagementAt(ADOPTED_AT, ADOPTED_AT.plusDays(13)))
                .isEqualTo(ADOPTED_AT.plusDays(14));
        assertThat(policy.nextManagementAt(ADOPTED_AT, ADOPTED_AT.plusDays(14)))
                .isEqualTo(ADOPTED_AT.plusDays(28));
    }

    @Test
    void 입양_시각이_없으면_지금을_주기_시작으로_본다() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 2, 10, 0);

        assertThat(policy.cycleStart(null, now)).isEqualTo(now);
        assertThat(policy.nextManagementAt(null, now)).isEqualTo(now.plusDays(14));
    }
}
