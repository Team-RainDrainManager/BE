package com.rainbutler.domain.adoption.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.rainbutler.support.TestPolicies;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * 정기 점검 주기(14일 · 겨울 28일)와 다음 관리 예정일 계산을 검증합니다.
 */
class ManagementCyclePolicyTest {

    private final ManagementCyclePolicy policy =
            new ManagementCyclePolicy(TestPolicies.defaults());

    @Test
    void 기본_주기는_14일이다() {
        assertThat(policy.cycleDays(LocalDate.of(2026, 3, 1))).isEqualTo(14);
        assertThat(policy.cycleDays(LocalDate.of(2026, 11, 30))).isEqualTo(14);
    }

    @Test
    void 겨울철_12월부터_2월까지는_28일이다() {
        assertThat(policy.cycleDays(LocalDate.of(2026, 12, 1))).isEqualTo(28);
        assertThat(policy.cycleDays(LocalDate.of(2027, 1, 15))).isEqualTo(28);
        assertThat(policy.cycleDays(LocalDate.of(2027, 2, 28))).isEqualTo(28);
    }

    @Test
    void 관리한_시각에_주기를_더해_다음_예정일을_구한다() {
        LocalDateTime managedAt = LocalDateTime.of(2026, 11, 1, 10, 30);

        assertThat(policy.nextManagementAt(managedAt))
                .isEqualTo(LocalDateTime.of(2026, 11, 15, 10, 30));
    }

    @Test
    void 겨울에_관리하면_다음_예정일도_28일_뒤다() {
        LocalDateTime managedAt = LocalDateTime.of(2026, 12, 20, 10, 30);

        assertThat(policy.nextManagementAt(managedAt))
                .isEqualTo(LocalDateTime.of(2027, 1, 17, 10, 30));
    }

    @Test
    void 겨울철_판정은_월만_본다() {
        assertThat(ManagementCyclePolicy.isWinter(LocalDate.of(2026, 12, 31))).isTrue();
        assertThat(ManagementCyclePolicy.isWinter(LocalDate.of(2027, 1, 1))).isTrue();
        assertThat(ManagementCyclePolicy.isWinter(LocalDate.of(2027, 2, 1))).isTrue();
        assertThat(ManagementCyclePolicy.isWinter(LocalDate.of(2027, 3, 1))).isFalse();
    }
}
