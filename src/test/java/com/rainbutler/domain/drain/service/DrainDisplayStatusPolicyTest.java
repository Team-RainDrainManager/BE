package com.rainbutler.domain.drain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.rainbutler.domain.adoption.entity.Adoption;
import com.rainbutler.domain.drain.entity.DrainDisplayStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * 지도 · 목록의 표시 상태 우선순위를 검증합니다.
 */
class DrainDisplayStatusPolicyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 3, 9, 0);

    private final DrainDisplayStatusPolicy policy = new DrainDisplayStatusPolicy();

    @Test
    void 입양되지_않았고_막힘_제보가_없으면_미입양이다() {
        assertThat(policy.resolve(null, false, NOW)).isEqualTo(DrainDisplayStatus.AVAILABLE);
    }

    @Test
    void 입양되지_않았어도_막힘_제보가_있으면_막힘_제보가_우선한다() {
        assertThat(policy.resolve(null, true, NOW))
                .isEqualTo(DrainDisplayStatus.BLOCKAGE_REPORTED);
    }

    @Test
    void 입양되었어도_막힘_제보가_있으면_막힘_제보가_우선한다() {
        Adoption adoption = adoption(NOW.plusDays(5));

        assertThat(policy.resolve(adoption, true, NOW))
                .isEqualTo(DrainDisplayStatus.BLOCKAGE_REPORTED);
    }

    @Test
    void 다음_점검일이_지났으면_점검_지연이다() {
        Adoption adoption = adoption(NOW.minusMinutes(1));

        assertThat(policy.resolve(adoption, false, NOW))
                .isEqualTo(DrainDisplayStatus.MANAGEMENT_DELAYED);
    }

    @Test
    void 다음_점검일이_남았으면_정상이다() {
        Adoption adoption = adoption(NOW.plusMinutes(1));

        assertThat(policy.resolve(adoption, false, NOW)).isEqualTo(DrainDisplayStatus.NORMAL);
    }

    @Test
    void 다음_점검일이_정확히_지금이면_정상이다() {
        Adoption adoption = adoption(NOW);

        assertThat(policy.resolve(adoption, false, NOW)).isEqualTo(DrainDisplayStatus.NORMAL);
    }

    @Test
    void 다음_점검일이_없으면_정상이다() {
        Adoption adoption = adoption(null);

        assertThat(policy.resolve(adoption, false, NOW)).isEqualTo(DrainDisplayStatus.NORMAL);
    }

    private Adoption adoption(LocalDateTime nextManagementAt) {
        Adoption adoption = mock(Adoption.class);
        given(adoption.getNextManagementAt()).willReturn(nextManagementAt);
        return adoption;
    }
}
