package com.rainbutler.domain.drain.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 성장 레벨 구간표의 다음 레벨 점수와 진행률 계산을 검증합니다.
 */
class GrowthLevelPolicyTest {

    /** 만렙(Lv.50)이 되는 최소 누적 점수 */
    private static final int MAX_LEVEL_SCORE = 5_978;

    private final GrowthLevelPolicy policy = new GrowthLevelPolicy();

    @Test
    void 레벨_1은_0점에서_시작하고_다음_레벨은_50점이다() {
        assertThat(policy.nextLevelScore(1, 0)).isEqualTo(50);
        assertThat(policy.progressPercent(1, 0)).isZero();
    }

    @Test
    void 현재_레벨_구간에서_진행률을_계산한다() {
        assertThat(policy.progressPercent(1, 25)).isEqualTo(50);
        assertThat(policy.progressPercent(2, 76)).isEqualTo(49);
        assertThat(policy.nextLevelScore(2, 76)).isEqualTo(103);
    }

    @Test
    void 레벨별_구간표를_따른다() {
        assertThat(policy.nextLevelScore(1, 0)).isEqualTo(50);
        assertThat(policy.nextLevelScore(2, 50)).isEqualTo(103);
        assertThat(policy.nextLevelScore(3, 103)).isEqualTo(159);
        assertThat(policy.nextLevelScore(4, 159)).isEqualTo(218);
        assertThat(policy.nextLevelScore(5, 218)).isEqualTo(280);
        assertThat(policy.nextLevelScore(6, 280)).isEqualTo(345);
        assertThat(policy.nextLevelScore(7, 345)).isEqualTo(413);
        assertThat(policy.nextLevelScore(8, 413)).isEqualTo(484);
        assertThat(policy.nextLevelScore(9, 484)).isEqualTo(558);
        assertThat(policy.nextLevelScore(10, 558)).isEqualTo(635);
        assertThat(policy.nextLevelScore(49, 5784)).isEqualTo(MAX_LEVEL_SCORE);
        assertThat(policy.progressPercent(5, 249)).isEqualTo(50);
        assertThat(policy.progressPercent(8, 449)).isEqualTo(50);
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
        assertThat(policy.nextLevelScore(GrowthLevelPolicy.MAX_LEVEL, MAX_LEVEL_SCORE))
                .isEqualTo(MAX_LEVEL_SCORE);
        assertThat(policy.progressPercent(GrowthLevelPolicy.MAX_LEVEL, MAX_LEVEL_SCORE))
                .isEqualTo(100);
    }

    @Test
    void 레벨이_범위를_벗어나도_계산이_깨지지_않는다() {
        // 0 이하는 1레벨로 맞춘다
        assertThat(policy.nextLevelScore(0, 0)).isEqualTo(50);
        assertThat(policy.progressPercent(0, 25)).isEqualTo(50);
        // 만렙 초과는 만렙으로 맞춘다
        int overMax = GrowthLevelPolicy.MAX_LEVEL + 1;
        assertThat(policy.nextLevelScore(overMax, MAX_LEVEL_SCORE)).isEqualTo(MAX_LEVEL_SCORE);
        assertThat(policy.progressPercent(overMax, 0)).isEqualTo(100);
    }

    @Test
    void 점수로_레벨을_구한다() {
        assertThat(policy.levelFor(0)).isEqualTo(1);
        assertThat(policy.levelFor(49)).isEqualTo(1);
        assertThat(policy.levelFor(50)).isEqualTo(2);
        assertThat(policy.levelFor(3300)).isEqualTo(34);
        assertThat(policy.levelFor(4400)).isEqualTo(41);
        assertThat(policy.levelFor(MAX_LEVEL_SCORE)).isEqualTo(GrowthLevelPolicy.MAX_LEVEL);
        assertThat(policy.levelFor(MAX_LEVEL_SCORE * 2)).isEqualTo(GrowthLevelPolicy.MAX_LEVEL);
    }

    @Test
    void 레벨별_칭호를_돌려주고_입양_전은_미입양이다() {
        assertThat(policy.titleFor(0)).isEqualTo("미입양");
        assertThat(policy.titleFor(-1)).isEqualTo("미입양");
        assertThat(policy.titleFor(1)).isEqualTo("씨앗");
        assertThat(policy.titleFor(3)).isEqualTo("떡잎");
        assertThat(policy.titleFor(10)).isEqualTo("전설");
        assertThat(policy.titleFor(11)).isEqualTo("신화");
        // TODO: 만렙 칭호를 확정하면 이 값도 함께 바꾼다
        assertThat(policy.titleFor(GrowthLevelPolicy.MAX_LEVEL)).isEqualTo("책임감 있는5");
        assertThat(policy.titleFor(GrowthLevelPolicy.MAX_LEVEL + 1))
                .isEqualTo(policy.titleFor(GrowthLevelPolicy.MAX_LEVEL));
    }
}