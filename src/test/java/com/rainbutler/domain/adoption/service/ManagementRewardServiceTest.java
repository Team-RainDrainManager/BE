package com.rainbutler.domain.adoption.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.rainbutler.domain.adoption.entity.Adoption;
import com.rainbutler.domain.adoption.entity.ManagementActionType;
import com.rainbutler.domain.adoption.entity.ManagementLog;
import com.rainbutler.domain.adoption.entity.ManagementType;
import com.rainbutler.domain.adoption.repository.ManagementLogRepository;
import com.rainbutler.domain.drain.entity.Drain;
import com.rainbutler.domain.drain.service.GrowthLevelPolicy;
import com.rainbutler.domain.point.entity.PointHistory;
import com.rainbutler.domain.point.entity.PointSourceType;
import com.rainbutler.domain.point.repository.PointHistoryRepository;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.global.config.PolicyProperties;
import com.rainbutler.support.TestPolicies;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 입양 후 관리 기록의 경험치 지급을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class ManagementRewardServiceTest {

    private static final Long USER_ID = 3L;
    private static final Long DRAIN_ID = 10L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 10, 0);
    private static final PolicyProperties POLICIES = TestPolicies.defaults();

    @Mock
    private ManagementLogRepository managementLogRepository;
    @Mock
    private PointHistoryRepository pointHistoryRepository;

    private ManagementRewardService service;
    private ManagementLog log;
    private Adoption adoption;
    private User user;

    @BeforeEach
    void setUp() {
        service = new ManagementRewardService(
                managementLogRepository, pointHistoryRepository,
                new GrowthLevelPolicy(), POLICIES);

        user = User.local("a@test.com", "{noop}x", "집사");
        ReflectionTestUtils.setField(user, "id", USER_ID);
        Drain drain = Drain.builder()
                .registeredBy(user)
                .latitude(37.5)
                .longitude(127.0)
                .photoUrl("drain-key")
                .dirtLevel(1)
                .isRisk(false)
                .build();
        ReflectionTestUtils.setField(drain, "id", DRAIN_ID);
        adoption = Adoption.builder()
                .user(user)
                .drain(drain)
                .nickname("또랑")
                .adoptedAt(NOW.minusDays(3))
                .build();
        log = ManagementLog.builder()
                .adoption(adoption)
                .type(ManagementType.REGULAR)
                .actionType(ManagementActionType.INSPECTION)
                .build();
        ReflectionTestUtils.setField(log, "id", 30L);
    }

    @Test
    void 하루_첫_관리면_개인_점수와_빗물받이_성장을_함께_준다() {
        given(pointHistoryRepository.existsBySourceTypeAndSourceId(
                PointSourceType.MANAGEMENT, 30L)).willReturn(false);
        given(managementLogRepository.countVerifiedOnDay(anyLong(), anyLong(), anyLong(),
                any(), any())).willReturn(0L);

        int xp = service.reward(log, NOW);

        assertThat(xp).isEqualTo(POLICIES.inspectionXp());
        assertThat(user.getPoint()).isEqualTo(POLICIES.inspectionXp());
        assertThat(adoption.getScore()).isEqualTo(POLICIES.inspectionXp());
        verify(pointHistoryRepository).save(any(PointHistory.class));
    }

    @Test
    void 같은_빗물받이를_같은_날_또_관리하면_성장_점수는_주지_않는다() {
        given(pointHistoryRepository.existsBySourceTypeAndSourceId(
                PointSourceType.MANAGEMENT, 30L)).willReturn(false);
        given(managementLogRepository.countVerifiedOnDay(anyLong(), anyLong(), anyLong(),
                any(), any())).willReturn(1L);

        int xp = service.reward(log, NOW);

        assertThat(xp).isZero();
        assertThat(user.getPoint()).isZero();
        assertThat(adoption.getScore()).isZero();
        verify(pointHistoryRepository, never()).save(any());
    }

    @Test
    void 이미_지급한_기록은_다시_지급하지_않는다() {
        given(pointHistoryRepository.existsBySourceTypeAndSourceId(
                PointSourceType.MANAGEMENT, 30L)).willReturn(true);
        lenient().when(managementLogRepository.countVerifiedOnDay(anyLong(), anyLong(),
                anyLong(), any(), any())).thenReturn(0L);

        int xp = service.reward(log, NOW);

        assertThat(xp).isZero();
        verify(pointHistoryRepository, never()).save(any());
    }
}
