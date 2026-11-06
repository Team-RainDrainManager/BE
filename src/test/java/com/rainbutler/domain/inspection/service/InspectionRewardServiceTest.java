package com.rainbutler.domain.inspection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.rainbutler.domain.adoption.entity.Adoption;
import com.rainbutler.domain.drain.entity.Drain;
import com.rainbutler.domain.drain.entity.DrainStatus;
import com.rainbutler.domain.drain.service.GrowthLevelPolicy;
import com.rainbutler.domain.inspection.entity.Inspection;
import com.rainbutler.domain.inspection.entity.InspectionKind;
import com.rainbutler.domain.inspection.repository.InspectionRepository;
import com.rainbutler.domain.point.entity.PointHistory;
import com.rainbutler.domain.point.repository.PointHistoryRepository;
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
 * 점검 경험치의 빗물받이별 하루 1회 · 중복 지급 방지를 확인합니다.
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
    private PointHistoryRepository pointHistoryRepository;

    private InspectionRewardService service;
    private User user;
    private Drain drain;
    private Adoption adoption;
    private Inspection inspection;

    @BeforeEach
    void setUp() {
        service = new InspectionRewardService(
                inspectionRepository, pointHistoryRepository, new GrowthLevelPolicy(), POLICIES);

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

        inspection = Inspection.builder()
                .user(user)
                .drain(drain)
                .adoption(adoption)
                .kind(InspectionKind.ROUTINE)
                .beforePhotoKey("before-key")
                .beforeGrade(1)
                .aiConfidence(BigDecimal.valueOf(0.9))
                .distanceM(1)
                .capturedAt(NOW)
                .build();
        ReflectionTestUtils.setField(inspection, "id", 30L);
    }

    @Test
    void 경험치를_개인_점수와_빗물받이_성장에_함께_지급한다() {
        given(inspectionRepository.countRewardedBetween(USER_ID, DRAIN_ID,
                NOW.toLocalDate().atStartOfDay(), NOW.toLocalDate().plusDays(1).atStartOfDay()))
                .willReturn(0L);

        int xp = service.reward(inspection, adoption, "정기 점검", NOW);

        assertThat(xp).isEqualTo(POLICIES.inspectionXp());
        assertThat(inspection.isRewarded()).isTrue();
        assertThat(inspection.getXpAwarded()).isEqualTo(POLICIES.inspectionXp());
        assertThat(user.getPoint()).isEqualTo(POLICIES.inspectionXp());
        assertThat(adoption.getScore()).isEqualTo(POLICIES.inspectionXp());
        verify(pointHistoryRepository).save(any(PointHistory.class));
    }

    @Test
    void 대신_점검은_개인_점수만_주고_빗물받이_성장은_올리지_않는다() {
        Inspection substitute = Inspection.builder()
                .user(user)
                .drain(drain)
                .adoption(adoption)
                .kind(InspectionKind.SUBSTITUTE)
                .beforePhotoKey("before-key")
                .beforeGrade(1)
                .aiConfidence(BigDecimal.valueOf(0.9))
                .distanceM(1)
                .capturedAt(NOW)
                .build();
        ReflectionTestUtils.setField(substitute, "id", 31L);
        given(inspectionRepository.countRewardedBetween(USER_ID, DRAIN_ID,
                NOW.toLocalDate().atStartOfDay(), NOW.toLocalDate().plusDays(1).atStartOfDay()))
                .willReturn(0L);

        int xp = service.reward(substitute, adoption, "대신 점검", NOW);

        assertThat(xp).isEqualTo(POLICIES.inspectionXp());
        assertThat(user.getPoint()).isEqualTo(POLICIES.inspectionXp());
        assertThat(adoption.getScore()).isZero();
        verify(pointHistoryRepository).save(any(PointHistory.class));
    }

    @Test
    void 같은_날_같은_빗물받이는_두_번째부터_경험치를_주지_않는다() {
        lenient().when(inspectionRepository.countRewardedBetween(any(), any(), any(), any()))
                .thenReturn(1L);

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
        inspection.reward(POLICIES.inspectionXp());

        int xp = service.reward(inspection, adoption, "정기 점검", NOW);

        assertThat(xp).isEqualTo(POLICIES.inspectionXp());
        assertThat(user.getPoint()).isZero();
        assertThat(adoption.getScore()).isZero();
        verifyNoInteractions(pointHistoryRepository);
    }
}
