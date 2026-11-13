package com.rainbutler.domain.adoption.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * 입양의 관리 기록 · 알림 발송 표시를 검증합니다.
 */
class AdoptionTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 31, 9, 0);

    @Test
    void 관리_기록을_저장하면_다음_예정일과_알림_표시를_새로_잡는다() {
        Adoption adoption = adoption();
        adoption.markManagementNotified(NOW);

        adoption.recordManagement(NOW, NOW.plusDays(14));

        assertThat(adoption.getLastManagedAt()).isEqualTo(NOW);
        assertThat(adoption.getNextManagementAt()).isEqualTo(NOW.plusDays(14));
        assertThat(adoption.isManagementNotified()).isFalse();
    }

    @Test
    void 빗물받이_별명을_바꾼다() {
        Adoption adoption = adoption();

        adoption.changeNickname("새또랑이");

        assertThat(adoption.getNickname()).isEqualTo("새또랑이");
    }

    private Adoption adoption() {
        return Adoption.builder()
                .nickname("또랑이")
                .adoptedAt(NOW)
                .build();
    }
}
