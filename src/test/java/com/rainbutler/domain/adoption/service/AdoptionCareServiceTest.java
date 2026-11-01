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
import com.rainbutler.domain.adoption.entity.ReleaseReason;
import com.rainbutler.domain.adoption.repository.AdoptionRepository;
import com.rainbutler.domain.drain.entity.Drain;
import com.rainbutler.domain.notification.entity.NotificationType;
import com.rainbutler.domain.push.service.PushService;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.service.PauseService;
import com.rainbutler.support.TestPolicies;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 미관리 경고 · 자동 파양 판정을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class AdoptionCareServiceTest {

    private static final Long USER_ID = 3L;
    private static final Long ADOPTION_ID = 10L;
    private static final Long DRAIN_ID = 20L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 31, 9, 0);

    @Mock
    private AdoptionRepository adoptionRepository;
    @Mock
    private AdoptionService adoptionService;
    @Mock
    private PushService pushService;
    @Mock
    private PauseService pauseService;

    private AdoptionCareService adoptionCareService;

    @BeforeEach
    void setUp() {
        adoptionCareService = new AdoptionCareService(adoptionRepository, adoptionService,
                pushService, pauseService, TestPolicies.defaults());
    }

    @Test
    void 관리_기준에서_35일이_지나면_자동_파양하고_요청을_재공개한다() {
        Adoption adoption = activeAdoption(NOW.minusDays(35));

        AdoptionCareResult result = adoptionCareService.care(ADOPTION_ID, NOW);

        assertThat(result).isEqualTo(AdoptionCareResult.RELEASED);
        verify(adoptionService).release(adoption, ReleaseReason.AUTO, NOW);
        verify(pushService).notify(eq(USER_ID), eq(NotificationType.ADOPTION_RELEASED),
                anyString(), anyString(), isNull(), eq(ADOPTION_ID), anyMap());
    }

    @Test
    void 관리_기준에서_28일이_지나면_경고를_한_번_보낸다() {
        Adoption adoption = activeAdoption(NOW.minusDays(28));
        given(adoption.isWarningSentSince(NOW.minusDays(28))).willReturn(false);

        AdoptionCareResult result = adoptionCareService.care(ADOPTION_ID, NOW);

        assertThat(result).isEqualTo(AdoptionCareResult.WARNED);
        verify(adoption).markWarned(NOW);
        verifyNoInteractions(adoptionService);
        verify(pushService).notify(eq(USER_ID), eq(NotificationType.ADOPTION_WARNING),
                anyString(), anyString(), isNull(), eq(ADOPTION_ID), anyMap());
    }

    @Test
    void 같은_관리_주기에_이미_경고했으면_다시_보내지_않는다() {
        Adoption adoption = activeAdoption(NOW.minusDays(28));
        given(adoption.isWarningSentSince(NOW.minusDays(28))).willReturn(true);

        AdoptionCareResult result = adoptionCareService.care(ADOPTION_ID, NOW);

        assertThat(result).isEqualTo(AdoptionCareResult.NONE);
        verify(adoption, never()).markWarned(NOW);
        verifyNoInteractions(pushService);
    }

    @Test
    void 아직_경고일이_되지_않았으면_아무것도_하지_않는다() {
        activeAdoption(NOW.minusDays(27));

        AdoptionCareResult result = adoptionCareService.care(ADOPTION_ID, NOW);

        assertThat(result).isEqualTo(AdoptionCareResult.NONE);
        verifyNoInteractions(pushService);
    }

    @Test
    void 점검_쉬기_중이면_건너뛴다() {
        Adoption adoption = activeAdoption(NOW.minusDays(35));
        User user = adoption.getUser();
        given(pauseService.isCareSuspended(user)).willReturn(true);

        AdoptionCareResult result = adoptionCareService.care(ADOPTION_ID, NOW);

        assertThat(result).isEqualTo(AdoptionCareResult.NONE);
        verifyNoInteractions(adoptionService);
        verifyNoInteractions(pushService);
    }

    @Test
    void 해제된_입양이면_건너뛴다() {
        Adoption adoption = mock(Adoption.class);
        given(adoptionRepository.findById(ADOPTION_ID)).willReturn(Optional.of(adoption));
        given(adoption.getStatus()).willReturn(AdoptionStatus.RELEASED);

        AdoptionCareResult result = adoptionCareService.care(ADOPTION_ID, NOW);

        assertThat(result).isEqualTo(AdoptionCareResult.NONE);
        verifyNoInteractions(pushService);
    }

    @Test
    void 없는_입양이면_건너뛴다() {
        given(adoptionRepository.findById(ADOPTION_ID)).willReturn(Optional.empty());

        AdoptionCareResult result = adoptionCareService.care(ADOPTION_ID, NOW);

        assertThat(result).isEqualTo(AdoptionCareResult.NONE);
        verifyNoInteractions(pushService);
    }

    private Adoption activeAdoption(LocalDateTime baseline) {
        Adoption adoption = mock(Adoption.class);
        User user = mock(User.class);
        Drain drain = mock(Drain.class);
        lenient().when(adoptionRepository.findById(ADOPTION_ID)).thenReturn(Optional.of(adoption));
        lenient().when(adoption.getStatus()).thenReturn(AdoptionStatus.ACTIVE);
        lenient().when(adoption.getUser()).thenReturn(user);
        lenient().when(user.getId()).thenReturn(USER_ID);
        lenient().when(pauseService.isCareSuspended(user)).thenReturn(false);
        lenient().when(pauseService.careBaseline(adoption, user)).thenReturn(baseline);
        lenient().when(adoption.getId()).thenReturn(ADOPTION_ID);
        lenient().when(adoption.getNickname()).thenReturn("또랑이");
        lenient().when(adoption.getDrain()).thenReturn(drain);
        lenient().when(drain.getId()).thenReturn(DRAIN_ID);
        return adoption;
    }
}
