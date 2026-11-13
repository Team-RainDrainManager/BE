package com.rainbutler.domain.point.service;

import com.rainbutler.global.config.PolicyProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 점검 · 청소 리워드 금액을 계산합니다.
 *
 * <p>금액은 활동 종류와 그 주기 안에서 몇 번째인지로 정합니다. 개인 참여 점수와 빗물받이 성장 점수에
 * 같은 값을 쓰며, 대신 점검은 개인 점수만 받습니다.
 *
 * <ul>
 *   <li>점검(청소 불필요): 그 주기 첫 점검 10, 두 번째부터 5
 *   <li>청소: 그 주기 첫 청소 30 고정, 두 번째부터 1단계 하락 10 · 2단계 하락 20
 *   <li>대신 점검: 주기와 무관하게 점검 10 · 청소 20
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ActivityRewardPolicy {

    private final PolicyProperties policyProperties;

    /**
     * 점검(청소 불필요) 경험치를 구합니다.
     *
     * @param rewardedInCycle 그 주기 안에서 이미 경험치를 받은 점검 수
     * @return 경험치
     */
    public int visitXp(long rewardedInCycle) {
        return rewardedInCycle == 0
                ? policyProperties.visitXp()
                : policyProperties.revisitXp();
    }

    /**
     * 청소 경험치를 구합니다.
     *
     * @param rewardedInCycle 그 주기 안에서 이미 경험치를 받은 청소 수
     * @param dropLevels 더러움 단계가 몇 단계 내려갔는지 (1~2)
     * @return 경험치
     */
    public int cleaningXp(long rewardedInCycle, int dropLevels) {
        if (rewardedInCycle == 0) {
            return policyProperties.firstCleaningXp();
        }
        return dropLevels >= 2
                ? policyProperties.cleaningTwoLevelXp()
                : policyProperties.cleaningOneLevelXp();
    }

    /**
     * 대신 점검 경험치를 구합니다. 주기와 관계없이 정해진 금액을 줍니다.
     *
     * @param cleaned 청소까지 인정됐으면 true (아니면 점검만)
     * @return 개인 경험치
     */
    public int substituteXp(boolean cleaned) {
        return cleaned
                ? policyProperties.substituteCleaningXp()
                : policyProperties.substituteVisitXp();
    }
}
