package com.rainbutler.domain.inspection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.rainbutler.domain.adoption.entity.Adoption;
import com.rainbutler.domain.adoption.repository.ManagementLogRepository;
import com.rainbutler.domain.adoption.service.ManagementCyclePolicy;
import com.rainbutler.domain.drain.entity.Drain;
import com.rainbutler.domain.drain.entity.DrainStatus;
import com.rainbutler.domain.drain.service.GrowthLevelPolicy;
import com.rainbutler.domain.inspection.entity.Inspection;
import com.rainbutler.domain.inspection.entity.InspectionKind;
import com.rainbutler.domain.inspection.repository.InspectionRepository;
import com.rainbutler.domain.point.entity.PointHistory;
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
 * 점검 · 청소 리워드 금액과 빗물받이별 하루 1회 · 중복 지급 방지를 확인합니다.
 */
@ExtendWith(MockitoExtension.class)
class InspectionRewardServiceTest {

    private static final Long USER_ID = 3L;
    private static final Long DRAIN_ID = 10L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 10, 0);
    private static final PolicyProperties POLICIES = TestPolicies.defaults();

    @Mock
    private InspectionRepository inspectionRepository;
    @Mock
    private ManagementLogRepository managementLogRepository;
    @Mock
    private PointHistoryRepository pointHistoryRepository;

    private InspectionRewardService service;
    private User user;
    private Drain drain;
    private Adoption adoption;

    @BeforeEach
    void setUp() {
        service = new InspectionRewardService(
                inspectionRepository, managementLogRepository, pointHistoryRepository,
                new GrowthLevelPolicy(), new ManagementCyclePolicy(POLICIES),
                new ActivityRewardPolicy(POLICIES));

        user = User.local("a@test.com", "{noop}x", "집사");
        ReflectionTestUtils.setField(user, "id", USER_ID);

        drain = Drain.builder()
                .registeredBy(user)
                .latitude(37.5)
                .longitude(127.0)
                .photoUrl("drain-key")
                .dirtLevel(1)
                .status(DrainStatus.ACTIVE)
                .isRisk(false)
                .build();
        ReflectionTestUtils.setField(drain, "id", DRAIN_ID);

        adoption = Adoption.builder()
                .user(user)
                .drain(drain)
                .nickname("또랑")
                .adoptedAt(NOW.minusDays(3))
                .build();
    }

    @Test
    void 그_주기_첫_점검이면_10점을_개인과_빗물받이에_함께_준다() {
        Inspection inspection = inspection(InspectionKind.ROUTINE, 1);

        int xp = service.reward(inspection, adoption, "정기 점검", NOW);

        assertThat(xp).isEqualTo(POLICIES.visitXp());
        assertThat(inspection.isRewarded()).isTrue();
        assertThat(inspection.getXpAwarded()).isEqualTo(POLICIES.visitXp());
        assertThat(user.getPoint()).isEqualTo(POLICIES.visitXp());
        assertThat(adoption.getScore()).isEqualTo(POLICIES.visitXp());
        verify(pointHistoryRepository).save(any(PointHistory.class));
    }

    @Test
    void 같은_주기_두_번째_점검이면_5점을_준다() {
        given(inspectionRepository.countVisitRewards(anyLong(), anyLong(), any(), any()))
                .willReturn(1L);
        Inspection inspection = inspection(InspectionKind.ROUTINE, 1);

        int xp = service.reward(inspection, adoption, "정기 점검", NOW);

        assertThat(xp).isEqualTo(POLICIES.revisitXp());
        assertThat(adoption.getScore()).isEqualTo(POLICIES.revisitXp());
    }

    @Test
    void 그_주기_첫_청소는_하락_단계와_관계없이_30점을_준다() {
        Inspection inspection = cleaningInspection(InspectionKind.ROUTINE, 3, 2);

        int xp = service.reward(inspection, adoption, "정기 점검", NOW);

        assertThat(xp).isEqualTo(POLICIES.firstCleaningXp());
        assertThat(user.getPoint()).isEqualTo(POLICIES.firstCleaningXp());
        assertThat(adoption.getScore()).isEqualTo(POLICIES.firstCleaningXp());
    }

    @Test
    void 두_번째_청소는_1단계_하락이면_10점_2단계_하락이면_20점을_준다() {
        given(inspectionRepository.countCleaningRewards(anyLong(), anyLong(), any(), any()))
                .willReturn(1L);

        Inspection oneStep = cleaningInspection(InspectionKind.ROUTINE, 3, 2);
        assertThat(service.reward(oneStep, adoption, "정기 점검", NOW))
                .isEqualTo(POLICIES.cleaningOneLevelXp());

        user = User.local("b@test.com", "{noop}x", "집사");
        ReflectionTestUtils.setField(user, "id", USER_ID);
        adoption = Adoption.builder()
                .user(user)
                .drain(drain)
                .nickname("또랑")
                .adoptedAt(NOW.minusDays(3))
                .build();
        Inspection twoSteps = cleaningInspection(InspectionKind.ROUTINE, 3, 1);

        assertThat(service.reward(twoSteps, adoption, "정기 점검", NOW))
                .isEqualTo(POLICIES.cleaningTwoLevelXp());
    }

    @Test
    void 대신_점검은_주기와_관계없이_10점을_개인에게만_준다() {
        Inspection substitute = inspection(InspectionKind.SUBSTITUTE, 1);

        int xp = service.reward(substitute, adoption, "대신 점검", NOW);

        assertThat(xp).isEqualTo(POLICIES.substituteVisitXp());
        assertThat(user.getPoint()).isEqualTo(POLICIES.substituteVisitXp());
        assertThat(adoption.getScore()).isZero();
        verifyNoInteractions(inspectionRepository);
    }

    @Test
    void 대신_점검_청소는_20점을_개인에게만_준다() {
        Inspection substitute = cleaningInspection(InspectionKind.SUBSTITUTE, 3, 2);

        int xp = service.reward(substitute, adoption, "대신 점검", NOW);

        assertThat(xp).isEqualTo(POLICIES.substituteCleaningXp());
        assertThat(user.getPoint()).isEqualTo(POLICIES.substituteCleaningXp());
        assertThat(adoption.getScore()).isZero();
    }

    @Test
    void 같은_날_같은_빗물받이는_두_번째부터_점수를_주지_않는다() {
        given(pointHistoryRepository.countDrainRewardsOnDay(anyLong(), anyLong(), any(), any()))
                .willReturn(1L);
        Inspection inspection = inspection(InspectionKind.ROUTINE, 1);

        int xp = service.reward(inspection, adoption, "정기 점검", NOW);

        assertThat(xp).isZero();
        assertThat(inspection.isRewarded()).isTrue();
        assertThat(inspection.getXpAwarded()).isZero();
        assertThat(user.getPoint()).isZero();
        assertThat(adoption.getScore()).isZero();
        verify(pointHistoryRepository, never()).save(any());
    }

    @Test
    void 이미_지급한_점검은_다시_지급하지_않는다() {
        Inspection inspection = inspection(InspectionKind.ROUTINE, 1);
        inspection.reward(POLICIES.visitXp());

        int xp = service.reward(inspection, adoption, "정기 점검", NOW);

        assertThat(xp).isEqualTo(POLICIES.visitXp());
        assertThat(user.getPoint()).isZero();
        assertThat(adoption.getScore()).isZero();
        verifyNoInteractions(pointHistoryRepository);
    }

    @Test
    void 청소한_날_같은_빗물받이_점검은_0점이고_다음_날_점검은_다시_10점이다() {
        Inspection cleaning = cleaningInspection(InspectionKind.ROUTINE, 3, 2);

        // 그날 처음 인정된 청소는 30점
        assertThat(service.reward(cleaning, adoption, "정기 점검", NOW))
                .isEqualTo(POLICIES.firstCleaningXp());

        // 같은 날 두 번째 기록(점검)은 하루 1회 제한으로 0점
        given(pointHistoryRepository.countDrainRewardsOnDay(anyLong(), anyLong(), any(), any()))
                .willReturn(1L);
        Inspection sameDay = inspection(InspectionKind.ROUTINE, 1);
        assertThat(service.reward(sameDay, adoption, "정기 점검", NOW)).isZero();
        assertThat(sameDay.isRewarded()).isTrue();

        // 다음 날에는 0점 점검이 첫 점검 자리를 쓰지 않아 다시 10점
        given(pointHistoryRepository.countDrainRewardsOnDay(anyLong(), anyLong(), any(), any()))
                .willReturn(0L);
        Inspection nextDay = inspection(InspectionKind.ROUTINE, 1);
        assertThat(service.reward(nextDay, adoption, "정기 점검", NOW.plusDays(1)))
                .isEqualTo(POLICIES.visitXp());
    }

    private Inspection inspection(InspectionKind kind, int beforeGrade) {
        Inspection inspection = Inspection.builder()
                .user(user)
                .drain(drain)
                .adoption(adoption)
                .kind(kind)
                .beforePhotoKey("before-key")
                .beforeGrade(beforeGrade)
                .aiConfidence(BigDecimal.valueOf(0.9))
                .distanceM(1)
                .capturedAt(NOW)
                .build();
        ReflectionTestUtils.setField(inspection, "id", 30L);
        return inspection;
    }

    private Inspection cleaningInspection(InspectionKind kind, int beforeGrade, int afterGrade) {
        Inspection inspection = inspection(kind, beforeGrade);
        inspection.completeCleaning("after-key", afterGrade, 0.9);
        return inspection;
    }
}
