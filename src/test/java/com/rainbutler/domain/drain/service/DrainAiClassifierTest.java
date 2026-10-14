package com.rainbutler.domain.drain.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 촬영기 등급 → 우리 스키마 매핑 검증입니다. (네트워크 호출 없음)
 */
class DrainAiClassifierTest {

    @ParameterizedTest(name = "촬영기 {0}등급 → 더러움 {1}단계")
    @CsvSource({"0, 1", "1, 2", "2, 3", "3, 3"})
    void gradeToDirtLevel(int grade, int expected) {
        assertThat(DrainAiClassifier.toDirtLevel(grade)).isEqualTo(expected);
    }

    @Test
    @DisplayName("신고 필요 또는 3등급이면 위험")
    void toRisk() {
        assertThat(DrainAiClassifier.toRisk(0, false)).isFalse();
        assertThat(DrainAiClassifier.toRisk(2, false)).isFalse();
        assertThat(DrainAiClassifier.toRisk(3, false)).isTrue();
        assertThat(DrainAiClassifier.toRisk(1, true)).isTrue();
    }
}
