package com.rainbutler.domain.adoption.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.rainbutler.domain.adoption.dto.Request.AdoptionReleaseReason;
import com.rainbutler.domain.adoption.dto.Request.AdoptionReleaseRequest;
import com.rainbutler.domain.adoption.dto.Response.AdoptionReleaseResponse;
import com.rainbutler.domain.adoption.entity.Adoption;
import com.rainbutler.domain.adoption.entity.AdoptionStatus;
import com.rainbutler.domain.adoption.entity.ReleaseReason;
import com.rainbutler.domain.adoption.repository.AdoptionRepository;
import com.rainbutler.domain.cleaning.repository.CleaningRepository;
import com.rainbutler.domain.drain.repository.DrainRepository;
import com.rainbutler.domain.request.entity.InspectionRequest;
import com.rainbutler.domain.request.entity.RequestStatus;
import com.rainbutler.domain.request.repository.InspectionRequestRepository;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.support.TestPolicies;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 입양 해제의 권한 · 중복 확인과 요청 재공개 호출을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class AdoptionServiceTest {

    private static final Long USER_ID = 3L;
    private static final Long ADOPTION_ID = 10L;
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-31T03:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    @Mock
    private AdoptionRepository adoptionRepository;
    @Mock
    private DrainRepository drainRepository;
    @Mock
    private CleaningRepository cleaningRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private InspectionRequestRepository inspectionRequestRepository;

    private AdoptionService adoptionService;

    @BeforeEach
    void setUp() {
        adoptionService = new AdoptionService(adoptionRepository, drainRepository,
                cleaningRepository, userRepository, inspectionRequestRepository,
                new ManagementCyclePolicy(TestPolicies.defaults()), CLOCK);
    }

    @Test
    void 본인_입양을_해제하고_요청을_재공개한다() {
        Adoption adoption = activeAdoption(USER_ID);
        InspectionRequest sent = mock(InspectionRequest.class);
        InspectionRequest accepted = mock(InspectionRequest.class);
        given(inspectionRequestRepository.findAllByAdoption_IdInAndStatusIn(
                List.of(ADOPTION_ID), List.of(RequestStatus.SENT, RequestStatus.ACCEPTED)))
                .willReturn(List.of(sent, accepted));

        AdoptionReleaseResponse response = adoptionService.releaseAdoption(
                USER_ID, ADOPTION_ID, new AdoptionReleaseRequest(AdoptionReleaseReason.MOVED));

        assertThat(response.adoptionId()).isEqualTo(ADOPTION_ID);
        assertThat(response.releasedAt()).isEqualTo(NOW);
        verify(adoption).release(ReleaseReason.MOVED, NOW);
        verify(sent).reopenAfterRelease(NOW);
        verify(accepted).reopenAfterRelease(NOW);
    }

    @Test
    void 재공개할_요청이_없어도_해제한다() {
        Adoption adoption = activeAdoption(USER_ID);
        given(inspectionRequestRepository.findAllByAdoption_IdInAndStatusIn(
                List.of(ADOPTION_ID), List.of(RequestStatus.SENT, RequestStatus.ACCEPTED)))
                .willReturn(List.of());

        adoptionService.releaseAdoption(
                USER_ID, ADOPTION_ID, new AdoptionReleaseRequest(AdoptionReleaseReason.OTHER));

        verify(adoption).release(ReleaseReason.OTHER, NOW);
    }

    @Test
    void 없는_입양이면_ADOPTION_NOT_FOUND() {
        given(adoptionRepository.findById(ADOPTION_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adoptionService.releaseAdoption(
                USER_ID, ADOPTION_ID, new AdoptionReleaseRequest(AdoptionReleaseReason.MOVED)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ADOPTION_NOT_FOUND);
        verifyNoInteractions(inspectionRequestRepository);
    }

    @Test
    void 다른_사람의_입양이면_FORBIDDEN() {
        Adoption adoption = activeAdoption(USER_ID + 1);
        given(adoptionRepository.findById(ADOPTION_ID)).willReturn(Optional.of(adoption));

        assertThatThrownBy(() -> adoptionService.releaseAdoption(
                USER_ID, ADOPTION_ID, new AdoptionReleaseRequest(AdoptionReleaseReason.MOVED)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
        verify(adoption, never()).release(any(), any());
        verifyNoInteractions(inspectionRequestRepository);
    }

    @Test
    void 이미_해제된_입양이면_DUPLICATE_RESOURCE() {
        Adoption adoption = activeAdoption(USER_ID);
        given(adoption.getStatus()).willReturn(AdoptionStatus.RELEASED);
        given(adoptionRepository.findById(ADOPTION_ID)).willReturn(Optional.of(adoption));

        assertThatThrownBy(() -> adoptionService.releaseAdoption(
                USER_ID, ADOPTION_ID, new AdoptionReleaseRequest(AdoptionReleaseReason.MOVED)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
        verify(adoption, never()).release(any(), any());
        verifyNoInteractions(inspectionRequestRepository);
    }

    private Adoption activeAdoption(Long ownerId) {
        Adoption adoption = mock(Adoption.class);
        User owner = mock(User.class);
        given(owner.getId()).willReturn(ownerId);
        given(adoption.getUser()).willReturn(owner);
        lenient().when(adoption.getStatus()).thenReturn(AdoptionStatus.ACTIVE);
        lenient().when(adoption.getId()).thenReturn(ADOPTION_ID);
        lenient().when(adoption.getReleasedAt()).thenReturn(NOW);
        given(adoptionRepository.findById(ADOPTION_ID)).willReturn(Optional.of(adoption));
        return adoption;
    }
}
