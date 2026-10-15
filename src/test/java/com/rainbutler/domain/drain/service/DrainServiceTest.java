package com.rainbutler.domain.drain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.rainbutler.domain.adoption.repository.AdoptionRepository;
import com.rainbutler.domain.drain.dto.Request.DrainCreateRequest;
import com.rainbutler.domain.drain.dto.Response.DrainCreateResponse;
import com.rainbutler.domain.drain.entity.Drain;
import com.rainbutler.domain.drain.repository.DrainRepository;
import com.rainbutler.domain.report.entity.Report;
import com.rainbutler.domain.report.repository.ReportRepository;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.global.config.PolicyProperties;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.storage.StorageService;
import com.rainbutler.global.storage.UploadPurpose;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 빗물받이 생성을 검증합니다. StorageService · DrainAiClassifier는 mock이라 S3 · Gemini 없이 동작합니다.
 */
@ExtendWith(MockitoExtension.class)
class DrainServiceTest {

    private static final Long USER_ID = 3L;
    private static final String PHOTO_KEY =
            "local/drain/3/3f2a9c1e-1234-4abc-9def-0123456789ab.jpg";
    private static final String READ_URL = "https://bucket.s3.amazonaws.com/read-url";

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

    private DrainService drainService;

    @BeforeEach
    void setUp() {
        PolicyProperties policy = new PolicyProperties(3, 14, 28, 28, 35, 300, 3, 50,
                new BigDecimal("0.7"), LocalTime.of(6, 0), LocalTime.of(20, 0), 90);
        drainService = new DrainService(drainRepository, adoptionRepository, userRepository,
                reportRepository, drainAiClassifier, policy, storageService);
    }

    @Test
    void 검증된_S3_key를_저장하고_AI에는_조회_URL을_넘기며_응답에는_조회_URL을_준다() {
        givenVerifiedPhotoAndUser();
        given(drainAiClassifier.assess(READ_URL))
                .willReturn(new DrainAiClassifier.Assessment(2, false, 0.95, "조금 쌓임", 1, false));
        given(drainRepository.save(any(Drain.class))).willAnswer(call -> call.getArgument(0));

        DrainCreateResponse response = drainService.createDrain(USER_ID, request(null));

        ArgumentCaptor<Drain> saved = ArgumentCaptor.forClass(Drain.class);
        verify(drainRepository).save(saved.capture());
        assertThat(saved.getValue().getPhotoUrl()).isEqualTo(PHOTO_KEY);
        assertThat(saved.getValue().getDirtLevel()).isEqualTo(2);
        assertThat(response.photoUrl()).isEqualTo(READ_URL);
        assertThat(response.riskRemind()).isFalse();
        verify(reportRepository, never()).save(any());
    }

    @Test
    void AI_확신도가_낮으면_검토용_제보에도_S3_key를_저장한다() {
        givenVerifiedPhotoAndUser();
        given(drainAiClassifier.assess(READ_URL))
                .willReturn(new DrainAiClassifier.Assessment(3, true, 0.4, "애매함", 3, false));
        given(drainRepository.save(any(Drain.class))).willAnswer(call -> call.getArgument(0));
        given(reportRepository.save(any(Report.class))).willAnswer(call -> call.getArgument(0));

        DrainCreateResponse response = drainService.createDrain(USER_ID, request(true));

        ArgumentCaptor<Report> report = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(report.capture());
        assertThat(report.getValue().getPhotoUrl()).isEqualTo(PHOTO_KEY);
        assertThat(response.riskRemind()).isTrue();
    }

    @Test
    void 같은_사진_key로_이미_등록된_빗물받이가_있으면_DUPLICATE_PHOTO() {
        given(storageService.verifyUploaded(USER_ID, PHOTO_KEY, UploadPurpose.DRAIN))
                .willReturn(PHOTO_KEY);
        given(userRepository.findById(USER_ID))
                .willReturn(Optional.of(User.local("a@test.com", "{noop}x", "집사")));
        given(drainRepository.existsByPhotoUrl(PHOTO_KEY)).willReturn(true);

        assertThatThrownBy(() -> drainService.createDrain(USER_ID, request(null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_PHOTO);
        verify(drainAiClassifier, never()).assess(anyString());
    }

    @Test
    void 사진_검증에_실패하면_AI_판정과_저장을_하지_않는다() {
        given(storageService.verifyUploaded(USER_ID, PHOTO_KEY, UploadPurpose.DRAIN))
                .willThrow(new BusinessException(ErrorCode.PHOTO_FORBIDDEN));

        assertThatThrownBy(() -> drainService.createDrain(USER_ID, request(null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PHOTO_FORBIDDEN);
        verify(drainAiClassifier, never()).assess(anyString());
        verify(drainRepository, never()).save(any());
    }

    @Test
    void S3가_설정되지_않았으면_우회하지_않고_STORAGE_NOT_CONFIGURED로_실패한다() {
        given(storageService.verifyUploaded(USER_ID, PHOTO_KEY, UploadPurpose.DRAIN))
                .willThrow(new BusinessException(ErrorCode.STORAGE_NOT_CONFIGURED));

        assertThatThrownBy(() -> drainService.createDrain(USER_ID, request(null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.STORAGE_NOT_CONFIGURED);
        verify(drainRepository, never()).save(any());
    }

    private void givenVerifiedPhotoAndUser() {
        given(storageService.verifyUploaded(USER_ID, PHOTO_KEY, UploadPurpose.DRAIN))
                .willReturn(PHOTO_KEY);
        given(storageService.toReadUrl(PHOTO_KEY)).willReturn(READ_URL);
        given(userRepository.findById(USER_ID))
                .willReturn(Optional.of(User.local("a@test.com", "{noop}x", "집사")));
    }

    private static DrainCreateRequest request(Boolean isRisk) {
        return new DrainCreateRequest(37.5, 127.0, PHOTO_KEY, isRisk, null);
    }
}
