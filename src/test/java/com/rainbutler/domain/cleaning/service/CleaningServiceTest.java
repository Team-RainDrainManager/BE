package com.rainbutler.domain.cleaning.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.rainbutler.domain.adoption.repository.AdoptionRepository;
import com.rainbutler.domain.cleaning.dto.Request.CleaningVerifyRequest;
import com.rainbutler.domain.cleaning.dto.Response.CleaningVerifyResponse;
import com.rainbutler.domain.cleaning.entity.Cleaning;
import com.rainbutler.domain.cleaning.entity.CleaningStatus;
import com.rainbutler.domain.cleaning.repository.CleaningRepository;
import com.rainbutler.domain.drain.entity.Drain;
import com.rainbutler.domain.drain.repository.DrainRepository;
import com.rainbutler.domain.drain.service.DrainAiClassifier;
import com.rainbutler.domain.report.repository.ReportRepository;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.global.storage.StorageService;
import com.rainbutler.global.storage.UploadPurpose;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 입양 청소 완료 인증 시 빗물받이의 최신 사진이 청소 후 사진으로 바뀌는지 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class CleaningServiceTest {

    private static final Long USER_ID = 3L;
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
                userRepository, reportRepository, drainAiClassifier, storageService);

        User owner = mock(User.class);
        lenient().when(owner.getId()).thenReturn(USER_ID);

        drain = mock(Drain.class);

        cleaning = mock(Cleaning.class);
        lenient().when(cleaning.getId()).thenReturn(CLEANING_ID);
        lenient().when(cleaning.getUser()).thenReturn(owner);
        lenient().when(cleaning.getDrain()).thenReturn(drain);
        lenient().when(cleaning.getStatus()).thenReturn(CleaningStatus.IN_PROGRESS);
        lenient().when(cleaning.getDirtLevel()).thenReturn(2);
        lenient().when(cleaning.getAttemptNo()).thenReturn(1);
        lenient().when(cleaningRepository.findById(CLEANING_ID)).thenReturn(Optional.of(cleaning));
        lenient().when(cleaningRepository.save(cleaning)).thenReturn(cleaning);

        lenient().when(storageService.verifyUploaded(USER_ID, "after-key", UploadPurpose.AFTER))
                .thenReturn("after-key");
        lenient().when(storageService.toReadUrl(anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0) + "-url");
    }

    @Test
    void 청소_인증에_통과하면_빗물받이_사진을_청소_후_사진으로_바꾼다() {
        given(drainAiClassifier.assess("after-key-url"))
                .willReturn(assessment(1, 0));

        CleaningVerifyResponse response =
                service.verifyCleaning(USER_ID, CLEANING_ID, new CleaningVerifyRequest("after-key"));

        assertThat(response.passed()).isTrue();
        verify(drain).updateLatestPhoto("after-key", 1);
    }

    @Test
    void 청소_인증에_실패하면_빗물받이_사진을_바꾸지_않는다() {
        given(drainAiClassifier.assess("after-key-url"))
                .willReturn(assessment(2, 1));

        CleaningVerifyResponse response =
                service.verifyCleaning(USER_ID, CLEANING_ID, new CleaningVerifyRequest("after-key"));

        assertThat(response.passed()).isFalse();
        verify(drain, never()).updateLatestPhoto(anyString(), any());
    }

    private DrainAiClassifier.Assessment assessment(int dirtLevel, int grade) {
        return new DrainAiClassifier.Assessment(dirtLevel, false, 0.9, "판정", grade, false);
    }
}
