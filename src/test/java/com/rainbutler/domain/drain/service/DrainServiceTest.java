package com.rainbutler.domain.drain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.rainbutler.domain.adoption.repository.AdoptionRepository;
import com.rainbutler.domain.drain.dto.Request.DrainAssessmentRequest;
import com.rainbutler.domain.drain.dto.Request.DrainCreateRequest;
import com.rainbutler.domain.drain.dto.Response.DrainAssessmentResponse;
import com.rainbutler.domain.drain.dto.Response.DrainCreateResponse;
import com.rainbutler.domain.drain.entity.Drain;
import com.rainbutler.domain.drain.repository.DrainRepository;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.global.config.PolicyProperties;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.storage.StorageService;
import com.rainbutler.global.storage.UploadPurpose;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Optional;
import java.util.Set;
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
    private static final Set<UploadPurpose> ASSESSABLE =
            Set.of(UploadPurpose.DRAIN, UploadPurpose.BEFORE, UploadPurpose.AFTER);

    @Mock
    private DrainRepository drainRepository;
    @Mock
    private AdoptionRepository adoptionRepository;
    @Mock
    private DrainAiClassifier drainAiClassifier;
    @Mock
    private DrainWriter drainWriter;
    @Mock
    private StorageService storageService;

    private DrainService drainService;

    @BeforeEach
    void setUp() {
        PolicyProperties policy = new PolicyProperties(3, 14, 28, 28, 35, 300, 3, 50,
                new BigDecimal("0.7"), LocalTime.of(6, 0), LocalTime.of(20, 0), 90,
                LocalTime.of(22, 0), LocalTime.of(8, 0), 7, 14, 3, 14, 15,
                LocalTime.of(20, 0), 60, new BigDecimal("5"), 3, 1, 1, LocalTime.of(21, 0),
                LocalTime.of(9, 0), 3, 30);
        drainService = new DrainService(drainRepository, adoptionRepository, drainAiClassifier,
                drainWriter, policy, storageService);
    }

    @Test
    void 검증된_S3_key를_저장하고_AI에는_조회_URL을_넘기며_응답에는_조회_URL을_준다() {
        givenVerifiedPhoto();
        given(drainAiClassifier.assess(READ_URL))
                .willReturn(new DrainAiClassifier.Assessment(2, false, 0.95, "조금 쌓임", 1, false));
        Drain savedDrain = Drain.builder()
                .registeredBy(User.local("a@test.com", "{noop}x", "집사"))
                .latitude(37.5)
                .longitude(127.0)
                .photoUrl(PHOTO_KEY)
                .dirtLevel(2)
                .status(com.rainbutler.domain.drain.entity.DrainStatus.ACTIVE)
                .isRisk(false)
                .build();
        given(drainWriter.save(any(), any(), any(), anyInt(), anyBoolean(), any()))
                .willReturn(new DrainWriter.SavedDrain(savedDrain, null));

        DrainCreateResponse response = drainService.createDrain(USER_ID, request(null));

        assertThat(response.photoUrl()).isEqualTo(READ_URL);
        assertThat(response.riskRemind()).isFalse();
    }

    @Test
    void AI_확신도가_낮으면_검토용_제보에도_S3_key를_저장한다() {
        givenVerifiedPhoto();
        given(drainAiClassifier.assess(READ_URL))
                .willReturn(new DrainAiClassifier.Assessment(3, true, 0.4, "애매함", 3, false));
        Drain savedDrain = Drain.builder()
                .registeredBy(User.local("a@test.com", "{noop}x", "집사"))
                .latitude(37.5)
                .longitude(127.0)
                .photoUrl(PHOTO_KEY)
                .dirtLevel(3)
                .status(com.rainbutler.domain.drain.entity.DrainStatus.ACTIVE)
                .isRisk(true)
                .build();
        given(drainWriter.save(any(), any(), any(), anyInt(), anyBoolean(), any()))
                .willReturn(new DrainWriter.SavedDrain(savedDrain, 99L));

        DrainCreateResponse response = drainService.createDrain(USER_ID, request(true));

        assertThat(response.riskRemind()).isTrue();
        assertThat(response.riskReportId()).isEqualTo(99L);
    }

    @Test
    void 같은_사진_key로_이미_등록된_빗물받이가_있으면_DUPLICATE_PHOTO() {
        given(storageService.verifyUploaded(USER_ID, PHOTO_KEY, UploadPurpose.DRAIN))
                .willReturn(PHOTO_KEY);
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
        verify(drainWriter, never()).save(any(), any(), any(), anyInt(), anyBoolean(), any());
    }

    @Test
    void S3가_설정되지_않았으면_우회하지_않고_STORAGE_NOT_CONFIGURED로_실패한다() {
        given(storageService.verifyUploaded(USER_ID, PHOTO_KEY, UploadPurpose.DRAIN))
                .willThrow(new BusinessException(ErrorCode.STORAGE_NOT_CONFIGURED));

        assertThatThrownBy(() -> drainService.createDrain(USER_ID, request(null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.STORAGE_NOT_CONFIGURED);
        verify(drainWriter, never()).save(any(), any(), any(), anyInt(), anyBoolean(), any());
    }

    @Test
    void 판독은_DRAIN_BEFORE_AFTER_사진만_검증하고_AI에는_조회_URL을_넘긴다() {
        String beforeKey = "local/before/3/3f2a9c1e-1234-4abc-9def-0123456789ab.jpg";
        given(storageService.verifyUploaded(USER_ID, beforeKey, ASSESSABLE)).willReturn(beforeKey);
        given(storageService.toReadUrl(beforeKey)).willReturn(READ_URL);
        given(drainAiClassifier.assess(READ_URL))
                .willReturn(new DrainAiClassifier.Assessment(3, true, 0.9, "많이 막힘", 2, true));

        DrainAssessmentResponse response =
                drainService.assessPhoto(USER_ID, new DrainAssessmentRequest(beforeKey));

        assertThat(response.dirtLevel()).isEqualTo(3);
        assertThat(response.isRisk()).isTrue();
        verify(drainRepository, never()).save(any());
    }

    @Test
    void 판독_사진_검증에_실패하면_AI를_부르지_않는다() {
        given(storageService.verifyUploaded(USER_ID, PHOTO_KEY, ASSESSABLE))
                .willThrow(new BusinessException(ErrorCode.PHOTO_FORBIDDEN));

        assertThatThrownBy(() ->
                drainService.assessPhoto(USER_ID, new DrainAssessmentRequest(PHOTO_KEY)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PHOTO_FORBIDDEN);
        verify(drainAiClassifier, never()).assess(anyString());
    }

    private void givenVerifiedPhoto() {
        given(storageService.verifyUploaded(USER_ID, PHOTO_KEY, UploadPurpose.DRAIN))
                .willReturn(PHOTO_KEY);
        given(storageService.toReadUrl(PHOTO_KEY)).willReturn(READ_URL);
    }

    private static DrainCreateRequest request(Boolean isRisk) {
        return new DrainCreateRequest(37.5, 127.0, PHOTO_KEY, isRisk, null);
    }
}
