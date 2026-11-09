package com.rainbutler.domain.cleaning.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.rainbutler.domain.adoption.entity.AdoptionStatus;
import com.rainbutler.domain.adoption.repository.AdoptionRepository;
import com.rainbutler.domain.cleaning.dto.Request.CleaningStartRequest;
import com.rainbutler.domain.cleaning.dto.Request.CleaningVerifyRequest;
import com.rainbutler.domain.cleaning.dto.Response.CleaningStartResponse;
import com.rainbutler.domain.cleaning.dto.Response.CleaningVerifyResponse;
import com.rainbutler.domain.cleaning.entity.Cleaning;
import com.rainbutler.domain.cleaning.entity.CleaningStatus;
import com.rainbutler.domain.cleaning.repository.CleaningRepository;
import com.rainbutler.domain.drain.entity.Drain;
import com.rainbutler.domain.drain.entity.DrainAdoptionStatus;
import com.rainbutler.domain.drain.repository.DrainRepository;
import com.rainbutler.domain.drain.service.DrainAiClassifier;
import com.rainbutler.domain.report.entity.Report;
import com.rainbutler.domain.report.repository.ReportRepository;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.storage.StorageService;
import com.rainbutler.global.storage.UploadPurpose;
import com.rainbutler.support.TestPolicies;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 입양 청소 시작 · 완료 인증의 AI 등급 판정과 빗물받이 최신 사진 갱신을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class CleaningServiceTest {

    private static final Long USER_ID = 3L;
    private static final Long DRAIN_ID = 5L;
    private static final Long CLEANING_ID = 7L;

    @Mock
    private CleaningRepository cleaningRepository;
    @Mock
    private DrainRepository drainRepository;
    @Mock
    private AdoptionRepository adoptionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ReportRepository reportRepository;
    @Mock
    private DrainAiClassifier drainAiClassifier;
    @Mock
    private StorageService storageService;

    private CleaningService service;
    private Cleaning cleaning;
    private Drain drain;

    @BeforeEach
    void setUp() {
        service = new CleaningService(cleaningRepository, drainRepository, adoptionRepository,
                userRepository, reportRepository, drainAiClassifier, storageService,
                TestPolicies.defaults());

        User owner = mock(User.class);
        lenient().when(owner.getId()).thenReturn(USER_ID);

        drain = mock(Drain.class);
        lenient().when(drain.getId()).thenReturn(DRAIN_ID);
        lenient().when(drain.getLatitude()).thenReturn(37.5);
        lenient().when(drain.getLongitude()).thenReturn(127.0);
        lenient().when(drain.getAdoptionStatus()).thenReturn(DrainAdoptionStatus.AVAILABLE);
        lenient().when(drainRepository.findById(DRAIN_ID)).thenReturn(Optional.of(drain));

        cleaning = mock(Cleaning.class);
        lenient().when(cleaning.getId()).thenReturn(CLEANING_ID);
        lenient().when(cleaning.getUser()).thenReturn(owner);
        lenient().when(cleaning.getDrain()).thenReturn(drain);
        lenient().when(cleaning.getStatus()).thenReturn(CleaningStatus.IN_PROGRESS);
        lenient().when(cleaning.getDirtLevel()).thenReturn(2);
        lenient().when(cleaning.getAttemptNo()).thenReturn(1);
        lenient().when(cleaning.getLatitude()).thenReturn(37.5);
        lenient().when(cleaning.getLongitude()).thenReturn(127.0);
        lenient().when(cleaning.isLocationMismatch()).thenReturn(false);
        lenient().when(cleaningRepository.findById(CLEANING_ID)).thenReturn(Optional.of(cleaning));
        lenient().when(cleaningRepository.save(any(Cleaning.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        lenient().when(storageService.verifyUploaded(USER_ID, "after-key", UploadPurpose.AFTER))
                .thenReturn("after-key");
        lenient().when(storageService.verifyUploaded(USER_ID, "before-key", UploadPurpose.BEFORE))
                .thenReturn("before-key");
        lenient().when(storageService.toReadUrl(anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0) + "-url");

        lenient().when(adoptionRepository.countByUser_IdAndStatus(USER_ID, AdoptionStatus.ACTIVE))
                .thenReturn(0L);
        lenient().when(cleaningRepository.existsByBeforePhotoUrl("before-key")).thenReturn(false);
        lenient().when(userRepository.findById(USER_ID)).thenReturn(Optional.of(owner));
    }

    @Test
    void 등급0이면_청소를_완료로_만들고_청소가_필요없다고_응답한다() {
        given(drainAiClassifier.assess("before-key-url")).willReturn(assessment(1, 0));

        CleaningStartResponse response = start();

        assertThat(response.grade()).isZero();
        assertThat(response.mustClean()).isFalse();
        assertThat(response.blocked()).isFalse();

        ArgumentCaptor<Cleaning> saved = ArgumentCaptor.forClass(Cleaning.class);
        verify(cleaningRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(CleaningStatus.COMPLETED);
        assertThat(saved.getValue().getCompletedAt()).isNotNull();
        verify(drain).updateLatestPhoto("before-key", 1);
    }

    @Test
    void 등급1이면_청소가_필요하다고_응답하고_사진을_바꾸지_않는다() {
        given(drainAiClassifier.assess("before-key-url")).willReturn(assessment(2, 1));

        CleaningStartResponse response = start();

        assertThat(response.grade()).isEqualTo(1);
        assertThat(response.mustClean()).isTrue();

        ArgumentCaptor<Cleaning> saved = ArgumentCaptor.forClass(Cleaning.class);
        verify(cleaningRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(CleaningStatus.IN_PROGRESS);
        verify(drain, never()).updateLatestPhoto(anyString(), any());
    }

    @Test
    void 등급2이면_dirtLevel이_3이어도_청소가_필요하다() {
        given(drainAiClassifier.assess("before-key-url")).willReturn(assessment(3, 2));

        CleaningStartResponse response = start();

        assertThat(response.grade()).isEqualTo(2);
        assertThat(response.mustClean()).isTrue();
        assertThat(response.blocked()).isFalse();
    }

    @Test
    void 등급2는_needsReport가_true여도_막힘_제보로_전환하지_않는다() {
        given(drainAiClassifier.assess("before-key-url"))
                .willReturn(new DrainAiClassifier.Assessment(3, false, 0.9, "판정", 2, true));

        CleaningStartResponse response = start();

        assertThat(response.blocked()).isFalse();
        assertThat(response.mustClean()).isTrue();
        verify(reportRepository, never()).save(any(Report.class));
    }

    @Test
    void 등급3이고_needsReport면_청소를_만들지_않고_제보로_전환한다() {
        given(drainAiClassifier.assess("before-key-url"))
                .willReturn(new DrainAiClassifier.Assessment(3, true, 0.9, "판정", 3, true));

        CleaningStartResponse response = start();

        assertThat(response.blocked()).isTrue();
        assertThat(response.mustClean()).isFalse();
        assertThat(response.cleaningId()).isNull();
        verify(cleaningRepository, never()).save(any(Cleaning.class));
        verify(reportRepository).save(any(Report.class));
    }

    @Test
    void 청소_인증에_통과하면_빗물받이_사진을_청소_후_사진으로_바꾼다() {
        given(drainAiClassifier.assess("after-key-url"))
                .willReturn(assessment(1, 0));

        CleaningVerifyResponse response = service.verifyCleaning(
                USER_ID, CLEANING_ID, new CleaningVerifyRequest("after-key", 37.5, 127.0));

        assertThat(response.passed()).isTrue();
        verify(drain).updateLatestPhoto("after-key", 1);
    }

    @Test
    void 청소_인증에_실패하면_빗물받이_사진을_바꾸지_않는다() {
        given(drainAiClassifier.assess("after-key-url"))
                .willReturn(assessment(2, 2));

        CleaningVerifyResponse response = service.verifyCleaning(
                USER_ID, CLEANING_ID, new CleaningVerifyRequest("after-key", 37.5, 127.0));

        assertThat(response.beforeGrade()).isEqualTo(2);
        assertThat(response.afterGrade()).isEqualTo(2);
        assertThat(response.passed()).isFalse();
        verify(drain, never()).updateLatestPhoto(anyString(), any());
    }

    @Test
    void 막힘에서_쌓임으로_좋아지면_청소_인증을_통과한다() {
        given(cleaning.getDirtLevel()).willReturn(3);
        given(drainAiClassifier.assess("after-key-url"))
                .willReturn(assessment(2, 2));

        CleaningVerifyResponse response = service.verifyCleaning(
                USER_ID, CLEANING_ID, new CleaningVerifyRequest("after-key", 37.5, 127.0));

        assertThat(response.beforeGrade()).isEqualTo(3);
        assertThat(response.passed()).isTrue();
        verify(drain).updateLatestPhoto("after-key", 2);
    }

    @Test
    void 청소_전_사진_위치가_빗물받이에서_50m_넘으면_청소를_시작하지_않는다() {
        assertThatThrownBy(() -> service.startAdoptionCleaning(
                USER_ID, DRAIN_ID, new CleaningStartRequest("before-key", 37.6, 127.0)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CLEANING_LOCATION_MISMATCH);

        verify(drainAiClassifier, never()).assess(anyString());
        verify(cleaningRepository, never()).save(any(Cleaning.class));
    }

    @Test
    void 청소_후_사진_위치가_빗물받이에서_50m_넘으면_인증하지_않는다() {
        assertThatThrownBy(() -> service.verifyCleaning(
                USER_ID, CLEANING_ID, new CleaningVerifyRequest("after-key", 37.6, 127.0)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CLEANING_LOCATION_MISMATCH);

        verify(cleaning, never()).incrementAttemptNo();
        verify(drainAiClassifier, never()).assess(anyString());
    }

    @Test
    void 청소_후_사진_위치가_청소_전_사진과_50m_넘게_차이_나면_인증하지_않는다() {
        given(cleaning.getLatitude()).willReturn(37.6);

        assertThatThrownBy(() -> service.verifyCleaning(
                USER_ID, CLEANING_ID, new CleaningVerifyRequest("after-key", 37.5, 127.0)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CLEANING_LOCATION_MISMATCH);

        verify(cleaning, never()).incrementAttemptNo();
        verify(drainAiClassifier, never()).assess(anyString());
    }

    private CleaningStartResponse start() {
        return service.startAdoptionCleaning(
                USER_ID, DRAIN_ID, new CleaningStartRequest("before-key", 37.5, 127.0));
    }

    private DrainAiClassifier.Assessment assessment(int dirtLevel, int grade) {
        return new DrainAiClassifier.Assessment(dirtLevel, false, 0.9, "판정", grade, false);
    }
}
