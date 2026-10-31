package com.rainbutler.support;

import com.rainbutler.global.config.PolicyProperties;
import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * 단위 테스트용 정책 값입니다. {@code application.yml}의 기본값과 같습니다.
 */
public final class TestPolicies {

    private TestPolicies() {
    }

    /**
     * 기본 정책 값을 만듭니다.
     *
     * @return 정책
     */
    public static PolicyProperties defaults() {
        return new PolicyProperties(3, 14, 28, 28, 35, 300, 3, 50, new BigDecimal("0.7"),
                LocalTime.of(6, 0), LocalTime.of(12, 0), 90, LocalTime.of(22, 0),
                LocalTime.of(8, 0), 7, 14, 3, 14, 15,
                LocalTime.of(20, 0), 60, new BigDecimal("5"), 3, 1, 1, LocalTime.of(21, 0),
                LocalTime.of(9, 0), 3, 30);
    }
}
