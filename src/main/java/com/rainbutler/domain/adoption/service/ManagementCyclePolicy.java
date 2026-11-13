package com.rainbutler.domain.adoption.service;

import com.rainbutler.global.config.PolicyProperties;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 정기 점검 주기 정책입니다.
 *
 * <p>주기는 14일 고정입니다. 입양 시점을 기준으로 2주씩 계속 돌아가고, 그 주기 안에 점검을 한 번
 * 이상 하면 됩니다. 예전에는 겨울철(12월 ~ 2월)에 28일로 늘렸지만, 이제 계절과 관계없이 14일입니다.
 *
 * <p>주기는 DB가 아니라 {@code rain-butler.policy} 설정에서 가져옵니다.
 */
@Component
@RequiredArgsConstructor
public class ManagementCyclePolicy {

    private final PolicyProperties policyProperties;

    /**
     * 정기 점검 주기를 구합니다.
     *
     * @return 주기 (일)
     */
    public int cycleDays() {
        return policyProperties.managementCycleDays();
    }

    /**
     * 입양 시점을 기준으로 지금이 속한 주기의 시작 시각을 구합니다.
     *
     * <p>입양 시점이 없으면 지금을 주기 시작으로 봅니다.
     *
     * @param adoptedAt 입양 시각
     * @param at 기준 시각
     * @return 주기 시작 시각
     */
    public LocalDateTime cycleStart(LocalDateTime adoptedAt, LocalDateTime at) {
        if (adoptedAt == null) {
            return at;
        }
        return adoptedAt.plusDays(cycleIndex(adoptedAt, at) * cycleDays());
    }

    /**
     * 입양 시점을 기준으로 지금이 속한 주기의 끝(= 다음 관리 예정일)을 구합니다.
     *
     * @param adoptedAt 입양 시각
     * @param at 기준 시각
     * @return 다음 관리 예정일
     */
    public LocalDateTime nextManagementAt(LocalDateTime adoptedAt, LocalDateTime at) {
        return cycleStart(adoptedAt, at).plusDays(cycleDays());
    }

    /**
     * 입양 시점부터 몇 번째 주기인지 구합니다. (0부터 시작)
     *
     * @param adoptedAt 입양 시각
     * @param at 기준 시각
     * @return 주기 번호
     */
    public long cycleIndex(LocalDateTime adoptedAt, LocalDateTime at) {
        if (adoptedAt == null || !at.isAfter(adoptedAt)) {
            return 0;
        }
        return Duration.between(adoptedAt, at).toDays() / cycleDays();
    }
}
