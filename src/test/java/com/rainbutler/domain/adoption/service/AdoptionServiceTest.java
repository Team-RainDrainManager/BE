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

import com.rainbutler.domain.adoption.dto.Request.AdoptionNicknameUpdateRequest;
import com.rainbutler.domain.adoption.dto.Request.AdoptionRegisterRequest;
import com.rainbutler.domain.adoption.dto.Request.AdoptionReleaseReason;
import com.rainbutler.domain.adoption.dto.Request.AdoptionReleaseRequest;
import com.rainbutler.domain.adoption.dto.Response.AdoptionNicknameCheckResponse;
import com.rainbutler.domain.adoption.dto.Response.AdoptionNicknameUpdateResponse;
import com.rainbutler.domain.adoption.dto.Response.AdoptionReleaseResponse;
import com.rainbutler.domain.adoption.entity.Adoption;
import com.rainbutler.domain.adoption.entity.AdoptionStatus;
import com.rainbutler.domain.adoption.entity.ReleaseReason;
import com.rainbutler.domain.adoption.repository.AdoptionRepository;
import com.rainbutler.domain.cleaning.entity.Cleaning;
import com.rainbutler.domain.cleaning.entity.CleaningStatus;
import com.rainbutler.domain.cleaning.repository.CleaningRepository;
import com.rainbutler.domain.drain.entity.Drain;
import com.rainbutler.domain.drain.entity.DrainAdoptionStatus;
import com.rainbutler.domain.drain.repository.DrainRepository;
import com.rainbutler.domain.report.repository.ReportRepository;
import com.rainbutler.domain.request.entity.InspectionRequest;
import com.rainbutler.domain.request.entity.RequestStatus;
import com.rainbutler.domain.request.repository.InspectionRequestRepository;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.global.entity.ReviewStatus;
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
    private static final Long DRAIN_ID = 5L;
    private static final Long CLEANING_ID = 20L;
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
    private ReportRepository reportRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private InspectionRequestRepository inspectionRequestRepository;

    private AdoptionService adoptionService;

    @BeforeEach
    void setUp() {
        adoptionService = new AdoptionService(adoptionRepository, drainRepository,
                cleaningRepository, reportRepository, userRepository,
                inspectionRequestRepository,
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

    @Test
    void 본인_입양의_별명을_바꾼다() {
        Adoption adoption = activeAdoption(USER_ID);
        given(adoption.getNickname()).willReturn("새또랑이");
        given(adoption.getUpdatedAt()).willReturn(NOW);

        AdoptionNicknameUpdateResponse response = adoptionService.updateNickname(
                USER_ID, ADOPTION_ID, new AdoptionNicknameUpdateRequest("새또랑이"));

        assertThat(response.adoptionId()).isEqualTo(ADOPTION_ID);
        assertThat(response.nickname()).isEqualTo("새또랑이");
        assertThat(response.updatedAt()).isEqualTo(NOW);
        verify(adoption).changeNickname("새또랑이");
        verify(adoptionRepository).flush();
    }

    @Test
    void 별명을_바꿀_입양이_없으면_ADOPTION_NOT_FOUND() {
        given(adoptionRepository.findById(ADOPTION_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adoptionService.updateNickname(
                USER_ID, ADOPTION_ID, new AdoptionNicknameUpdateRequest("새또랑이")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ADOPTION_NOT_FOUND);
        verify(adoptionRepository, never()).flush();
    }

    @Test
    void 다른_사람의_입양_별명은_바꿀_수_없다() {
        Adoption adoption = activeAdoption(USER_ID + 1);

        assertThatThrownBy(() -> adoptionService.updateNickname(
                USER_ID, ADOPTION_ID, new AdoptionNicknameUpdateRequest("새또랑이")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
        verify(adoption, never()).changeNickname(any());
        verify(adoptionRepository, never()).flush();
    }

    @Test
    void 해제된_입양의_별명은_바꿀_수_없다() {
        Adoption adoption = activeAdoption(USER_ID);
        given(adoption.getStatus()).willReturn(AdoptionStatus.RELEASED);

        assertThatThrownBy(() -> adoptionService.updateNickname(
                USER_ID, ADOPTION_ID, new AdoptionNicknameUpdateRequest("새또랑이")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ADOPTION_NOT_FOUND);
        verify(adoption, never()).changeNickname(any());
        verify(adoptionRepository, never()).flush();
    }

    @Test
    void 사용할_수_있는_별명이면_available_true() {
        AdoptionNicknameCheckResponse response = adoptionService.checkNickname("  또랑이  ", null);

        assertThat(response.nickname()).isEqualTo("또랑이");
        assertThat(response.available()).isTrue();
        verify(adoptionRepository).existsByNicknameIgnoreCaseAndStatus(
                "또랑이", AdoptionStatus.ACTIVE);
    }

    @Test
    void 다른_입양이_쓰는_별명이면_available_false() {
        given(adoptionRepository.existsByNicknameIgnoreCaseAndStatus(
                "또랑이", AdoptionStatus.ACTIVE)).willReturn(true);

        AdoptionNicknameCheckResponse response = adoptionService.checkNickname("또랑이", null);

        assertThat(response.available()).isFalse();
    }

    @Test
    void 별명을_바꿀_때는_본인_입양을_중복에서_뺀다() {
        Adoption adoption = activeAdoption(USER_ID);
        given(adoption.getNickname()).willReturn("또랑이");
        given(adoption.getUpdatedAt()).willReturn(NOW);

        adoptionService.updateNickname(
                USER_ID, ADOPTION_ID, new AdoptionNicknameUpdateRequest(" 또랑이 "));

        verify(adoptionRepository).existsByNicknameIgnoreCaseAndStatusAndIdNot(
                "또랑이", AdoptionStatus.ACTIVE, ADOPTION_ID);
        verify(adoption).changeNickname("또랑이");
    }

    @Test
    void 다른_입양이_쓰는_별명으로는_바꿀_수_없다() {
        Adoption adoption = activeAdoption(USER_ID);
        given(adoptionRepository.existsByNicknameIgnoreCaseAndStatusAndIdNot(
                "새또랑이", AdoptionStatus.ACTIVE, ADOPTION_ID)).willReturn(true);

        assertThatThrownBy(() -> adoptionService.updateNickname(
                USER_ID, ADOPTION_ID, new AdoptionNicknameUpdateRequest("새또랑이")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NICKNAME_ALREADY_EXISTS);
        verify(adoption, never()).changeNickname(any());
        verify(adoptionRepository, never()).flush();
    }

    @Test
    void 이미_쓰는_별명으로는_입양할_수_없다() {
        given(adoptionRepository.existsByNicknameIgnoreCaseAndStatus(
                "또랑이", AdoptionStatus.ACTIVE)).willReturn(true);

        assertThatThrownBy(this::register)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NICKNAME_ALREADY_EXISTS);
        verify(adoptionRepository, never()).save(any());
        verifyNoInteractions(drainRepository);
    }

    @Test
    void 입양을_막는_제보가_있는_빗물받이는_입양할_수_없다() {        givenRegisterableDrain();
        given(reportRepository.existsByDrain_IdAndBlocksAdoptionTrueAndStatusIn(any(), any()))
                .willReturn(true);

        assertThatThrownBy(this::register)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DRAIN_ADOPTION_BLOCKED);

        verify(adoptionRepository, never()).save(any());
        verifyNoInteractions(cleaningRepository);
    }

    @Test
    void 위치가_불일치한_청소로는_입양할_수_없다() {
        Drain drain = givenRegisterableDrain();
        Cleaning cleaning = completedCleaning(drain, ReviewStatus.NOT_REQUIRED, true);
        given(cleaningRepository.findById(CLEANING_ID)).willReturn(Optional.of(cleaning));

        assertThatThrownBy(this::register)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CLEANING_LOCATION_MISMATCH);

        verify(adoptionRepository, never()).save(any());
    }

    @Test
    void 관리자_검토_대기_중인_청소로는_입양할_수_없다() {
        Drain drain = givenRegisterableDrain();
        Cleaning cleaning = completedCleaning(drain, ReviewStatus.PENDING, false);
        given(cleaningRepository.findById(CLEANING_ID)).willReturn(Optional.of(cleaning));

        assertThatThrownBy(this::register)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CLEANING_UNDER_REVIEW);

        verify(adoptionRepository, never()).save(any());
    }

    @Test
    void 반려된_청소로는_입양할_수_없다() {
        Drain drain = givenRegisterableDrain();
        Cleaning cleaning = completedCleaning(drain, ReviewStatus.REJECTED, false);
        given(cleaningRepository.findById(CLEANING_ID)).willReturn(Optional.of(cleaning));

        assertThatThrownBy(this::register)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CLEANING_REJECTED);

        verify(adoptionRepository, never()).save(any());
    }

    private void register() {
        adoptionService.registerAdoption(USER_ID, DRAIN_ID,
                new AdoptionRegisterRequest(CLEANING_ID, "또랑이", true));
    }

    private Drain givenRegisterableDrain() {
        Drain drain = mock(Drain.class);
        lenient().when(drain.getId()).thenReturn(DRAIN_ID);
        lenient().when(drain.getAdoptionStatus()).thenReturn(DrainAdoptionStatus.AVAILABLE);
        lenient().when(drainRepository.findById(DRAIN_ID)).thenReturn(Optional.of(drain));
        lenient().when(adoptionRepository.countByUser_IdAndStatus(
                USER_ID, AdoptionStatus.ACTIVE)).thenReturn(0L);
        return drain;
    }

    private Cleaning completedCleaning(
            Drain drain, ReviewStatus reviewStatus, boolean locationMismatch) {
        Cleaning cleaning = mock(Cleaning.class);
        User owner = mock(User.class);
        lenient().when(owner.getId()).thenReturn(USER_ID);
        lenient().when(cleaning.getUser()).thenReturn(owner);
        lenient().when(cleaning.getStatus()).thenReturn(CleaningStatus.COMPLETED);
        lenient().when(cleaning.getReviewStatus()).thenReturn(reviewStatus);
        lenient().when(cleaning.isLocationMismatch()).thenReturn(locationMismatch);
        lenient().when(cleaning.getDrain()).thenReturn(drain);
        return cleaning;
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
