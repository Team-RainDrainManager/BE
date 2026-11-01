package com.rainbutler.domain.adoption.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.rainbutler.domain.adoption.entity.Adoption;
import com.rainbutler.domain.adoption.entity.AdoptionStatus;
import com.rainbutler.domain.adoption.repository.AdoptionRepository;
import com.rainbutler.domain.drain.entity.Drain;
import com.rainbutler.domain.notification.entity.NotificationType;
import com.rainbutler.domain.push.service.PushService;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.service.PauseService;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 관리 예정일 하루 전 알림의 대상 판정을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class ManagementDueServiceTest {

    private static final Long USER_ID = 3L;
    private static final Long ADOPTION_ID = 10L;
    private static final Long DRAIN_ID = 20L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 31, 9, 0);
    private static final LocalDateTime TOMORROW = NOW.plusDays(1);

    @Mock
    private AdoptionRepository adoptionRepository;
    @Mock
    private PushService pushService;
    @Mock
    private PauseService pauseService;

    private ManagementDueService managementDueService;

    @BeforeEach
    void setUp() {
        managementDueService =
                new ManagementDueService(adoptionRepository, pushService, pauseService);
    }

    @Test
    void 관리_예정일이_내일이면_알림을_보낸다() {
        Adoption adoption = activeAdoption(TOMORROW);

        boolean notified = managementDueService.notifyDue(ADOPTION_ID, NOW);

        assertThat(notified).isTrue();
        verify(adoption).markManagementNotified(NOW);
        verify(pushService).notify(eq(USER_ID), eq(NotificationType.MANAGEMENT_DUE),
                anyString(), anyString(), isNull(), eq(ADOPTION_ID), anyMap());
    }

    @Test
    void 관리_예정일이_내일이_아니면_보내지_않는다() {
        Adoption adoption = activeAdoption(NOW.plusDays(2));

        boolean notified = managementDueService.notifyDue(ADOPTION_ID, NOW);

        assertThat(notified).isFalse();
        verify(adoption, never()).markManagementNotified(NOW);
        verifyNoInteractions(pushService);
    }

    @Test
    void 관리_예정일이_지났으면_보내지_않는다() {
        Adoption adoption = activeAdoption(NOW.minusDays(1));

        boolean notified = managementDueService.notifyDue(ADOPTION_ID, NOW);

        assertThat(notified).isFalse();
        verifyNoInteractions(pushService);
    }

    @Test
    void 이번_주기에_이미_알림을_보냈으면_다시_보내지_않는다() {
        Adoption adoption = activeAdoption(TOMORROW);
        given(adoption.isManagementNotified()).willReturn(true);

        boolean notified = managementDueService.notifyDue(ADOPTION_ID, NOW);

        assertThat(notified).isFalse();
        verify(adoption, never()).markManagementNotified(NOW);
        verifyNoInteractions(pushService);
    }

    @Test
    void 점검_쉬기_중이면_건너뛴다() {
        Adoption adoption = activeAdoption(TOMORROW);
        given(pauseService.isCareSuspended(adoption.getUser())).willReturn(true);

        boolean notified = managementDueService.notifyDue(ADOPTION_ID, NOW);

        assertThat(notified).isFalse();
        verify(adoption, never()).markManagementNotified(NOW);
        verifyNoInteractions(pushService);
    }

    @Test
    void 다음_관리_예정일이_없으면_건너뛴다() {
        Adoption adoption = activeAdoption(null);

        boolean notified = managementDueService.notifyDue(ADOPTION_ID, NOW);

        assertThat(notified).isFalse();
        verifyNoInteractions(pushService);
    }

    @Test
    void 해제된_입양이면_건너뛴다() {
        Adoption adoption = mock(Adoption.class);
        given(adoptionRepository.findById(ADOPTION_ID)).willReturn(Optional.of(adoption));
        given(adoption.getStatus()).willReturn(AdoptionStatus.RELEASED);

        boolean notified = managementDueService.notifyDue(ADOPTION_ID, NOW);

        assertThat(notified).isFalse();
        verifyNoInteractions(pushService);
    }

    @Test
    void 없는_입양이면_건너뛴다() {
        given(adoptionRepository.findById(ADOPTION_ID)).willReturn(Optional.empty());

        boolean notified = managementDueService.notifyDue(ADOPTION_ID, NOW);

        assertThat(notified).isFalse();
        verifyNoInteractions(pushService);
    }

    private Adoption activeAdoption(LocalDateTime nextManagementAt) {
        Adoption adoption = mock(Adoption.class);
        User user = mock(User.class);
        Drain drain = mock(Drain.class);
        lenient().when(adoptionRepository.findById(ADOPTION_ID)).thenReturn(Optional.of(adoption));
        lenient().when(adoption.getStatus()).thenReturn(AdoptionStatus.ACTIVE);
        lenient().when(adoption.getUser()).thenReturn(user);
        lenient().when(user.getId()).thenReturn(USER_ID);
        lenient().when(adoption.getNextManagementAt()).thenReturn(nextManagementAt);
        lenient().when(adoption.getId()).thenReturn(ADOPTION_ID);
        lenient().when(adoption.getNickname()).thenReturn("또랑이");
        lenient().when(adoption.getDrain()).thenReturn(drain);
        lenient().when(drain.getId()).thenReturn(DRAIN_ID);
        return adoption;
    }
}
