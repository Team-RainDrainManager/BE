package com.rainbutler.domain.inspection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

import com.rainbutler.domain.adoption.entity.Adoption;
import com.rainbutler.domain.adoption.entity.AdoptionStatus;
import com.rainbutler.domain.adoption.repository.AdoptionRepository;
import com.rainbutler.domain.drain.entity.Drain;
import com.rainbutler.domain.drain.repository.DrainRepository;
import com.rainbutler.domain.inspection.dto.InspectionEligibilityReason;
import com.rainbutler.domain.inspection.dto.InspectionEligibilityResponse;
import com.rainbutler.domain.request.entity.InspectionRequest;
import com.rainbutler.domain.request.entity.RequestStatus;
import com.rainbutler.domain.request.repository.InspectionRequestRepository;
import com.rainbutler.domain.sanction.service.SanctionService;
import com.rainbutler.domain.user.entity.User;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 정기·비 예보·대신 점검의 화면 진입 권한을 검증합니다. */
@ExtendWith(MockitoExtension.class)
class InspectionEligibilityServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long DRAIN_ID = 10L;
    private static final Long REQUEST_ID = 20L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 10, 0);

    @Mock
    private DrainRepository drainRepository;
    @Mock
    private AdoptionRepository adoptionRepository;
    @Mock
    private InspectionRequestRepository inspectionRequestRepository;
    @Mock
    private SanctionService sanctionService;

    private InspectionEligibilityService inspectionEligibilityService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.from(NOW.atZone(ZoneId.of("Asia/Seoul"))),
                ZoneId.of("Asia/Seoul"));
        inspectionEligibilityService = new InspectionEligibilityService(drainRepository,
                adoptionRepository, inspectionRequestRepository, sanctionService, clock);
        given(drainRepository.existsById(DRAIN_ID)).willReturn(true);
        given(sanctionService.isSanctioned(USER_ID)).willReturn(false);
    }

    @Test
    void 본인_ACTIVE_입양이면_정기_점검할_수_있다() {
        Adoption adoption = adoption(USER_ID);
        given(adoptionRepository.findByDrain_IdAndStatus(DRAIN_ID, AdoptionStatus.ACTIVE))
                .willReturn(Optional.of(adoption));

        InspectionEligibilityResponse result = inspectionEligibilityService.check(
                USER_ID, DRAIN_ID, null);

        assertThat(result.eligible()).isTrue();
        assertThat(result.reason()).isNull();
    }

    @Test
    void 다른_사람의_입양은_정기_점검할_수_없다() {
        Adoption otherAdoption = adoption(OTHER_USER_ID);
        given(adoptionRepository.findByDrain_IdAndStatus(DRAIN_ID, AdoptionStatus.ACTIVE))
                .willReturn(Optional.of(otherAdoption));

        InspectionEligibilityResponse result = inspectionEligibilityService.check(
                USER_ID, DRAIN_ID, null);

        assertThat(result.eligible()).isFalse();
        assertThat(result.reason()).isEqualTo(InspectionEligibilityReason.NOT_ASSIGNED);
    }

    @Test
    void 제재_중이면_점검할_수_없다() {
        given(sanctionService.isSanctioned(USER_ID)).willReturn(true);

        InspectionEligibilityResponse result = inspectionEligibilityService.check(
                USER_ID, DRAIN_ID, null);

        assertThat(result.eligible()).isFalse();
        assertThat(result.reason()).isEqualTo(InspectionEligibilityReason.SANCTIONED);
    }

    @Test
    void SENT_비_예보_요청의_입양자는_먼저_응답해야_한다() {
        InspectionRequest request = request(RequestStatus.SENT, USER_ID, null, true);
        given(inspectionRequestRepository.findById(REQUEST_ID))
                .willReturn(Optional.of(request));

        InspectionEligibilityResponse result = inspectionEligibilityService.check(
                USER_ID, DRAIN_ID, REQUEST_ID);

        assertThat(result.eligible()).isFalse();
        assertThat(result.reason()).isEqualTo(InspectionEligibilityReason.REPLY_REQUIRED);
    }

    @Test
    void ACCEPTED_비_예보_요청의_입양자는_점검할_수_있다() {
        InspectionRequest request = request(RequestStatus.ACCEPTED, USER_ID, null, true);
        given(inspectionRequestRepository.findById(REQUEST_ID))
                .willReturn(Optional.of(request));

        InspectionEligibilityResponse result = inspectionEligibilityService.check(
                USER_ID, DRAIN_ID, REQUEST_ID);

        assertThat(result.eligible()).isTrue();
    }

    @Test
    void CLAIMED_요청의_대신_점검자만_점검할_수_있다() {
        InspectionRequest request = request(RequestStatus.CLAIMED, OTHER_USER_ID, USER_ID, true);
        given(inspectionRequestRepository.findById(REQUEST_ID))
                .willReturn(Optional.of(request));

        InspectionEligibilityResponse result = inspectionEligibilityService.check(
                USER_ID, DRAIN_ID, REQUEST_ID);

        assertThat(result.eligible()).isTrue();
    }

    @Test
    void 완료_마감이_지났으면_요청이_종료됐다() {
        InspectionRequest request = request(RequestStatus.ACCEPTED, USER_ID, null, true);
        given(request.getCompleteDeadlineAt()).willReturn(NOW);
        given(inspectionRequestRepository.findById(REQUEST_ID)).willReturn(Optional.of(request));

        InspectionEligibilityResponse result = inspectionEligibilityService.check(
                USER_ID, DRAIN_ID, REQUEST_ID);

        assertThat(result.eligible()).isFalse();
        assertThat(result.reason()).isEqualTo(InspectionEligibilityReason.REQUEST_CLOSED);
    }

    private Adoption adoption(Long ownerId) {
        Adoption adoption = org.mockito.Mockito.mock(Adoption.class);
        User owner = org.mockito.Mockito.mock(User.class);
        lenient().when(adoption.getUser()).thenReturn(owner);
        lenient().when(owner.getId()).thenReturn(ownerId);
        return adoption;
    }

    private InspectionRequest request(RequestStatus status, Long ownerId, Long claimantId,
            boolean inProgress) {
        InspectionRequest request = org.mockito.Mockito.mock(InspectionRequest.class);
        Drain drain = org.mockito.Mockito.mock(Drain.class);
        lenient().when(request.getDrain()).thenReturn(drain);
        lenient().when(drain.getId()).thenReturn(DRAIN_ID);
        lenient().when(request.getStatus()).thenReturn(status);
        lenient().when(request.isInProgress()).thenReturn(inProgress);
        lenient().when(request.getCompleteDeadlineAt()).thenReturn(NOW.plusHours(1));
        if (ownerId != null) {
            Adoption adoption = adoption(ownerId);
            lenient().when(request.getAdoption()).thenReturn(adoption);
        }
        if (claimantId != null) {
            User claimant = org.mockito.Mockito.mock(User.class);
            lenient().when(request.getSubstituteUser()).thenReturn(claimant);
            lenient().when(claimant.getId()).thenReturn(claimantId);
        }
        return request;
    }
}
