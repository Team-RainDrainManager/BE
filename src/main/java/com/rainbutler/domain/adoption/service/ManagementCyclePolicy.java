package com.rainbutler.domain.adoption.service;

import com.rainbutler.global.config.PolicyProperties;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 정기 점검 주기 정책입니다.
 *
 * <p>기본 주기는 14일이고, 겨울철(12월 ~ 2월)에는 28일로 늘립니다. 겨울에는 낙엽 · 쓰레기가 덜 쌓여
 * 점검 간격을 길게 잡아도 되기 때문입니다.
 *
 * <p>주기는 DB가 아니라 {@code rain-butler.policy} 설정에서 가져옵니다.
 */
@Component
@RequiredArgsConstructor
public class ManagementCyclePolicy {

    private final PolicyProperties policyProperties;

    /**
     * 그날 기준 정기 점검 주기를 구합니다.
     *
     * @param date 기준 날짜
     * @return 주기 (일)
     */
    public int cycleDays(LocalDate date) {
        return isWinter(date)
                ? policyProperties.winterManagementCycleDays()
                : policyProperties.managementCycleDays();
    }

    /**
     * 관리(점검 · 청소)를 마친 시각으로 다음 관리 예정일을 구합니다.
     *
     * @param managedAt 관리한 시각
     * @return 다음 관리 예정일
     */
    public LocalDateTime nextManagementAt(LocalDateTime managedAt) {
        return managedAt.plusDays(cycleDays(managedAt.toLocalDate()));
    }

    /**
     * 겨울철(12월 ~ 2월)인지 확인합니다.
     *
     * @param date 기준 날짜
     * @return 겨울철이면 true
     */
    public static boolean isWinter(LocalDate date) {
        int month = date.getMonthValue();
        return month == 12 || month <= 2;
    }
}
