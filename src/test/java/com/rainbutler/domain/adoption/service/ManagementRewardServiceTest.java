package com.rainbutler.domain.adoption.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.rainbutler.domain.adoption.entity.Adoption;
import com.rainbutler.domain.adoption.entity.ManagementActionType;
import com.rainbutler.domain.adoption.entity.ManagementLog;
import com.rainbutler.domain.adoption.entity.ManagementType;
import com.rainbutler.domain.adoption.repository.ManagementLogRepository;
import com.rainbutler.domain.drain.entity.Drain;
import com.rainbutler.domain.drain.service.GrowthLevelPolicy;
import com.rainbutler.domain.inspection.repository.InspectionRepository;
import com.rainbutler.domain.point.entity.PointHistory;
import com.rainbutler.domain.point.entity.PointSourceType;
import com.rainbutler.domain.point.repository.PointHistoryRepository;
import com.rainbutler.domain.point.service.ActivityRewardPolicy;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.global.config.PolicyProperties;
import com.rainbutler.support.TestPolicies;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 입양 후 관리 기록의 경험치 지급을 검증합니다. (점검 · 청소 공통 정책)
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
    private InspectionRepository inspectionRepository;
    @Mock
    private PointHistoryRepository pointHistoryRepository;

    private ManagementRewardService service;
    private ManagementLog log;
    private Adoption adoption;
    private User user;

    @BeforeEach
    void setUp() {
        service = new ManagementRewardService(
                managementLogRepository, inspectionRepository, pointHistoryRepository,
                new GrowthLevelPolicy(), new ManagementCyclePolicy(POLICIES),
                new ActivityRewardPolicy(POLICIES));

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
        log = log(ManagementActionType.INSPECTION, 1, null);
    }

    @Test
    void 하루_첫_점검이면_개인_점수와_빗물받이_성장을_10점씩_준다() {
        int xp = service.reward(log, NOW);

        assertThat(xp).isEqualTo(POLICIES.visitXp());
        assertThat(user.getPoint()).isEqualTo(POLICIES.visitXp());
        assertThat(adoption.getScore()).isEqualTo(POLICIES.visitXp());
        verify(pointHistoryRepository).save(any(PointHistory.class));
    }

    @Test
    void 같은_주기_두_번째_점검이면_5점을_준다() {
        given(inspectionRepository.countVisitRewards(anyLong(), anyLong(), any(), any()))
                .willReturn(1L);

        int xp = service.reward(log, NOW);

        assertThat(xp).isEqualTo(POLICIES.revisitXp());
        assertThat(user.getPoint()).isEqualTo(POLICIES.revisitXp());
    }

    @Test
    void 같은_빗물받이를_같은_날_또_관리하면_점수를_주지_않는다() {
        given(pointHistoryRepository.countDrainRewardsOnDay(
                anyLong(), anyLong(), any(), any())).willReturn(1L);

        int xp = service.reward(log, NOW);

        assertThat(xp).isZero();
        assertThat(user.getPoint()).isZero();
        assertThat(adoption.getScore()).isZero();
        verify(pointHistoryRepository, never()).save(any());
    }

    @Test
    void 그_주기_첫_청소는_하락_단계와_관계없이_30점을_준다() {
        log = log(ManagementActionType.CLEANING, 3, 2);

        int xp = service.reward(log, NOW);

        assertThat(xp).isEqualTo(POLICIES.firstCleaningXp());
        assertThat(adoption.getScore()).isEqualTo(POLICIES.firstCleaningXp());
    }

    @Test
    void 두_번째_청소는_1단계_하락이면_10점_2단계_하락이면_20점을_준다() {
        given(inspectionRepository.countCleaningRewards(anyLong(), anyLong(), any(), any()))
                .willReturn(1L);

        log = log(ManagementActionType.CLEANING, 3, 2);
        assertThat(service.reward(log, NOW)).isEqualTo(POLICIES.cleaningOneLevelXp());

        user = User.local("b@test.com", "{noop}x", "집사");
        ReflectionTestUtils.setField(user, "id", USER_ID);
        adoption = Adoption.builder()
                .user(user)
                .drain(log.getAdoption().getDrain())
                .nickname("또랑")
                .adoptedAt(NOW.minusDays(3))
                .build();
        log = log(ManagementActionType.CLEANING, 3, 1);

        assertThat(service.reward(log, NOW)).isEqualTo(POLICIES.cleaningTwoLevelXp());
    }

    @Test
    void 이미_지급한_기록은_다시_지급하지_않는다() {
        given(pointHistoryRepository.existsBySourceTypeAndSourceId(
                PointSourceType.MANAGEMENT, 30L)).willReturn(true);
        lenient().when(pointHistoryRepository.countDrainRewardsOnDay(
                anyLong(), anyLong(), any(), any())).thenReturn(0L);

        int xp = service.reward(log, NOW);

        assertThat(xp).isZero();
        verify(pointHistoryRepository, never()).save(any());
    }

    private ManagementLog log(ManagementActionType actionType, int before, Integer after) {
        ManagementLog saved = ManagementLog.builder()
                .adoption(adoption)
                .type(ManagementType.REGULAR)
                .actionType(actionType)
                .build();
        saved.recordPhotos("before-key", after == null ? null : "after-key",
                before, after, BigDecimal.valueOf(0.9), null, 37.5, 127.0, false);
        saved.markVerified(NOW);
        ReflectionTestUtils.setField(saved, "id", 30L);
        return saved;
    }
}
