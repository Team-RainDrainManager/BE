package com.rainbutler.domain.drain.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 성장 레벨 구간표의 다음 레벨 점수와 진행률 계산을 검증합니다.
 */
class GrowthLevelPolicyTest {

    private final GrowthLevelPolicy policy = new GrowthLevelPolicy();

    @Test
    void 레벨_1은_0점에서_시작하고_다음_레벨은_50점이다() {
        assertThat(policy.nextLevelScore(1, 0)).isEqualTo(50);
        assertThat(policy.progressPercent(1, 0)).isZero();
    }

    @Test
    void 현재_레벨_구간에서_진행률을_계산한다() {
        assertThat(policy.progressPercent(1, 25)).isEqualTo(50);
        assertThat(policy.progressPercent(2, 100)).isEqualTo(50);
        assertThat(policy.nextLevelScore(2, 100)).isEqualTo(150);
    }

    @Test
    void 레벨별_구간표를_따른다() {
        assertThat(policy.nextLevelScore(3, 150)).isEqualTo(300);
        assertThat(policy.nextLevelScore(4, 300)).isEqualTo(500);
        assertThat(policy.nextLevelScore(5, 500)).isEqualTo(800);
        assertThat(policy.nextLevelScore(6, 800)).isEqualTo(1200);
        assertThat(policy.nextLevelScore(7, 1200)).isEqualTo(1700);
        assertThat(policy.nextLevelScore(9, 2400)).isEqualTo(3300);
        assertThat(policy.progressPercent(5, 650)).isEqualTo(50);
        assertThat(policy.progressPercent(8, 2050)).isEqualTo(50);
    }

    @Test
    void 점수가_구간을_넘어도_진행률은_100을_넘지_않는다() {
        assertThat(policy.progressPercent(2, 300)).isEqualTo(100);
    }

    @Test
    void 점수가_레벨_시작점보다_낮아도_진행률은_0이다() {
        assertThat(policy.progressPercent(3, 100)).isZero();
    }

    @Test
    void 만렙이면_다음_레벨_점수는_현재_점수이고_진행률은_100이다() {
        assertThat(policy.nextLevelScore(GrowthLevelPolicy.MAX_LEVEL, 4000)).isEqualTo(4000);
        assertThat(policy.progressPercent(GrowthLevelPolicy.MAX_LEVEL, 4000)).isEqualTo(100);
    }

    @Test
    void 레벨이_범위를_벗어나도_계산이_깨지지_않는다() {
        assertThat(policy.nextLevelScore(0, 0)).isEqualTo(50);
        assertThat(policy.progressPercent(0, 25)).isEqualTo(50);
        assertThat(policy.progressPercent(11, 0)).isEqualTo(100);
    }
}
