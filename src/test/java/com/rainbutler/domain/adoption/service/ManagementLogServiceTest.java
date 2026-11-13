package com.rainbutler.domain.adoption.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.rainbutler.domain.adoption.dto.Request.ManagementLogRequest;
import com.rainbutler.domain.adoption.dto.Response.ManagementLogResponse;
import com.rainbutler.domain.adoption.entity.Adoption;
import com.rainbutler.domain.adoption.entity.AdoptionStatus;
import com.rainbutler.domain.adoption.entity.ManagementActionType;
import com.rainbutler.domain.adoption.entity.ManagementLog;
import com.rainbutler.domain.adoption.entity.ManagementStatus;
import com.rainbutler.domain.adoption.entity.ManagementType;
import com.rainbutler.domain.adoption.repository.AdoptionRepository;
import com.rainbutler.domain.adoption.repository.ManagementLogRepository;
import com.rainbutler.domain.drain.entity.Drain;
import com.rainbutler.domain.drain.service.DrainAiClassifier;
import com.rainbutler.domain.request.entity.InspectionRequest;
import com.rainbutler.domain.request.repository.InspectionRequestRepository;
import com.rainbutler.domain.request.service.InspectionRequestService;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.global.config.PolicyProperties;
import com.rainbutler.global.entity.ReviewStatus;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.storage.StorageService;
import com.rainbutler.global.storage.UploadPurpose;
import com.rainbutler.support.TestPolicies;
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

/**
 * 관리 기록의 권한 · 입력 검증 · AI 인증 · 관리 주기 갱신 · 비 예보 요청 완료를 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class ManagementLogServiceTest {

    private static final Long USER_ID = 3L;
    private static final Long ADOPTION_ID = 10L;
    private static final Long REQUEST_ID = 20L;
    private static final double DRAIN_LAT = 37.5;
    private static final double DRAIN_LNG = 127.0;
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-31T03:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    private static final PolicyProperties POLICIES = TestPolicies.defaults();

    @Mock
    private AdoptionRepository adoptionRepository;
    @Mock
    private ManagementLogRepository managementLogRepository;
    @Mock
    private InspectionRequestRepository inspectionRequestRepository;
    @Mock
    private InspectionRequestService inspectionRequestService;
    @Mock
    private StorageService storageService;
    @Mock
    private DrainAiClassifier drainAiClassifier;
    @Mock
    private ManagementRewardService managementRewardService;
    @Mock
    private UserRepository userRepository;

    private ManagementLogService service;
    private Adoption adoption;
    private Drain drain;

    @BeforeEach
    void setUp() {
        service = new ManagementLogService(adoptionRepository, managementLogRepository,
                inspectionRequestRepository, inspectionRequestService, storageService,
                drainAiClassifier, new ManagementCyclePolicy(POLICIES), managementRewardService,
                POLICIES, userRepository, CLOCK);

        drain = mock(Drain.class);
        lenient().when(drain.getLatitude()).thenReturn(DRAIN_LAT);
        lenient().when(drain.getLongitude()).thenReturn(DRAIN_LNG);

        adoption = mock(Adoption.class);
        User owner = mock(User.class);
        lenient().when(owner.getId()).thenReturn(USER_ID);
        lenient().when(adoption.getId()).thenReturn(ADOPTION_ID);
        lenient().when(adoption.getUser()).thenReturn(owner);
        lenient().when(adoption.getDrain()).thenReturn(drain);
        lenient().when(adoption.getStatus()).thenReturn(AdoptionStatus.ACTIVE);
        lenient().when(adoptionRepository.findById(ADOPTION_ID))
                .thenReturn(Optional.of(adoption));

        lenient().when(storageService.verifyUploaded(any(), anyString(), any(UploadPurpose.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));
        lenient().when(storageService.toReadUrl(anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0) + "-url");
        lenient().when(managementLogRepository.save(any(ManagementLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void 정기_점검을_기록하면_관리_주기를_갱신하고_빗물받이_사진을_바꾼다() {
        assessment("before-key", 2, 0.9, 2);
        given(adoption.getNextManagementAt())
                .willReturn(NOW.plusDays(POLICIES.managementCycleDays()));

        ManagementLogResponse response = service.record(
                USER_ID, ADOPTION_ID, request(ManagementType.REGULAR, null,
                        ManagementActionType.INSPECTION, "before-key", null, DRAIN_LAT, DRAIN_LNG));

        assertThat(response.status()).isEqualTo(ManagementStatus.VERIFIED);
        assertThat(response.reviewStatus()).isEqualTo(ReviewStatus.NOT_REQUIRED);
        assertThat(response.managedAt()).isEqualTo(NOW);
        assertThat(response.nextManagementAt())
                .isEqualTo(NOW.plusDays(POLICIES.managementCycleDays()));
        assertThat(response.locationMismatch()).isFalse();
        assertThat(response.beforeDirtLevel()).isEqualTo(2);

        verify(adoption).recordManagement(NOW, NOW.plusDays(POLICIES.managementCycleDays()));
        verify(drain).updateLatestPhoto("before-key", 2);
        verifyNoInteractions(inspectionRequestService);
    }

    @Test
    void 청소까지_하면_청소_후_사진과_등급으로_판정한다() {
        assessment("before-key", 2, 0.9, 2);
        assessment("after-key", 1, 0.9, 1);

        ManagementLogResponse response = service.record(
                USER_ID, ADOPTION_ID, request(ManagementType.REGULAR, null,
                        ManagementActionType.CLEANING, "before-key", "after-key",
                        DRAIN_LAT, DRAIN_LNG));

        assertThat(response.status()).isEqualTo(ManagementStatus.VERIFIED);
        assertThat(response.beforeDirtLevel()).isEqualTo(2);
        assertThat(response.afterDirtLevel()).isEqualTo(1);
        verify(drain).updateLatestPhoto("after-key", 1);
    }

    @Test
    void 청소_후_등급이_좋아지지_않으면_FAILED_이고_관리_주기를_갱신하지_않는다() {
        assessment("before-key", 2, 0.9, 2);
        assessment("after-key", 2, 0.9, 2);

        ManagementLogResponse response = service.record(
                USER_ID, ADOPTION_ID, request(ManagementType.REGULAR, null,
                        ManagementActionType.CLEANING, "before-key", "after-key",
                        DRAIN_LAT, DRAIN_LNG));

        assertThat(response.status()).isEqualTo(ManagementStatus.FAILED);
        assertThat(response.managedAt()).isNull();
        verify(adoption, never()).recordManagement(any(), any());
        verify(drain, never()).updateLatestPhoto(anyString(), any());
    }

    @Test
    void 위치가_멀면_검토_대기로_남긴다() {
        assessment("before-key", 2, 0.9, 2);

        ManagementLogResponse response = service.record(
                USER_ID, ADOPTION_ID, request(ManagementType.REGULAR, null,
                        ManagementActionType.INSPECTION, "before-key", null,
                        DRAIN_LAT + 0.01, DRAIN_LNG + 0.01));

        assertThat(response.locationMismatch()).isTrue();
        assertThat(response.reviewStatus()).isEqualTo(ReviewStatus.PENDING);
        assertThat(response.status()).isEqualTo(ManagementStatus.VERIFIED);
    }

    @Test
    void AI_확신도가_낮으면_검토_대기로_남긴다() {
        assessment("before-key", 2, 0.5, 2);

        ManagementLogResponse response = service.record(
                USER_ID, ADOPTION_ID, request(ManagementType.REGULAR, null,
                        ManagementActionType.INSPECTION, "before-key", null,
                        DRAIN_LAT, DRAIN_LNG));

        assertThat(response.reviewStatus()).isEqualTo(ReviewStatus.PENDING);
    }

    @Test
    void 비_예보_점검은_연결된_요청을_완료하고_무응답_횟수를_초기화한다() {
        assessment("before-key", 2, 0.9, 2);
        InspectionRequest inspectionRequest = inspectionRequest();

        ManagementLogResponse response = service.record(
                USER_ID, ADOPTION_ID, request(ManagementType.RAIN, REQUEST_ID,
                        ManagementActionType.INSPECTION, "before-key", null,
                        DRAIN_LAT, DRAIN_LNG));

        assertThat(response.type()).isEqualTo(ManagementType.RAIN);
        verify(inspectionRequestService).complete(REQUEST_ID, USER_ID, NOW);
        verify(adoption).resetIgnoreCount();
    }

    @Test
    void 다른_사람의_입양이면_FORBIDDEN() {
        User other = mock(User.class);
        given(other.getId()).willReturn(USER_ID + 1);
        given(adoption.getUser()).willReturn(other);

        assertThatThrownBy(() -> service.record(USER_ID, ADOPTION_ID,
                request(ManagementType.REGULAR, null, ManagementActionType.INSPECTION,
                        "before-key", null, DRAIN_LAT, DRAIN_LNG)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void 입양_중이_아니면_INVALID_INPUT() {
        given(adoption.getStatus()).willReturn(AdoptionStatus.RELEASED);

        assertThatThrownBy(() -> service.record(USER_ID, ADOPTION_ID,
                request(ManagementType.REGULAR, null, ManagementActionType.INSPECTION,
                        "before-key", null, DRAIN_LAT, DRAIN_LNG)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void 안전_동의가_없으면_INVALID_INPUT() {
        ManagementLogRequest request = new ManagementLogRequest(
                ManagementType.REGULAR, null, ManagementActionType.INSPECTION,
                "before-key", null, null, DRAIN_LAT, DRAIN_LNG, false);

        assertThatThrownBy(() -> service.record(USER_ID, ADOPTION_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void 정기_점검에_요청_id를_보내면_INVALID_INPUT() {
        assertThatThrownBy(() -> service.record(USER_ID, ADOPTION_ID,
                request(ManagementType.REGULAR, REQUEST_ID, ManagementActionType.INSPECTION,
                        "before-key", null, DRAIN_LAT, DRAIN_LNG)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void 비_예보인데_요청_id가_없으면_INVALID_INPUT() {
        assertThatThrownBy(() -> service.record(USER_ID, ADOPTION_ID,
                request(ManagementType.RAIN, null, ManagementActionType.INSPECTION,
                        "before-key", null, DRAIN_LAT, DRAIN_LNG)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void 비_예보_요청이_없으면_REQUEST_NOT_FOUND() {
        given(inspectionRequestRepository.findById(REQUEST_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.record(USER_ID, ADOPTION_ID,
                request(ManagementType.RAIN, REQUEST_ID, ManagementActionType.INSPECTION,
                        "before-key", null, DRAIN_LAT, DRAIN_LNG)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REQUEST_NOT_FOUND);
    }

    @Test
    void 다른_입양의_비_예보_요청이면_REQUEST_NOT_COMPLETABLE() {
        Adoption other = mock(Adoption.class);
        given(other.getId()).willReturn(ADOPTION_ID + 1);
        InspectionRequest inspectionRequest = mock(InspectionRequest.class);
        given(inspectionRequest.getAdoption()).willReturn(other);
        given(inspectionRequestRepository.findById(REQUEST_ID))
                .willReturn(Optional.of(inspectionRequest));

        assertThatThrownBy(() -> service.record(USER_ID, ADOPTION_ID,
                request(ManagementType.RAIN, REQUEST_ID, ManagementActionType.INSPECTION,
                        "before-key", null, DRAIN_LAT, DRAIN_LNG)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REQUEST_NOT_COMPLETABLE);
    }

    @Test
    void 이미_기록한_비_예보_요청이면_DUPLICATE_RESOURCE() {
        inspectionRequest();
        given(managementLogRepository.existsByRequest_Id(REQUEST_ID)).willReturn(true);

        assertThatThrownBy(() -> service.record(USER_ID, ADOPTION_ID,
                request(ManagementType.RAIN, REQUEST_ID, ManagementActionType.INSPECTION,
                        "before-key", null, DRAIN_LAT, DRAIN_LNG)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
    }

    @Test
    void 청소인데_후사진이_없으면_INVALID_INPUT() {
        assertThatThrownBy(() -> service.record(USER_ID, ADOPTION_ID,
                request(ManagementType.REGULAR, null, ManagementActionType.CLEANING,
                        "before-key", null, DRAIN_LAT, DRAIN_LNG)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void 살펴보기인데_후사진을_보내면_INVALID_INPUT() {
        assertThatThrownBy(() -> service.record(USER_ID, ADOPTION_ID,
                request(ManagementType.REGULAR, null, ManagementActionType.INSPECTION,
                        "before-key", "after-key", DRAIN_LAT, DRAIN_LNG)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void 중복된_사진이면_DUPLICATE_PHOTO() {
        given(managementLogRepository.existsByBeforePhotoUrl("before-key")).willReturn(true);

        assertThatThrownBy(() -> service.record(USER_ID, ADOPTION_ID,
                request(ManagementType.REGULAR, null, ManagementActionType.INSPECTION,
                        "before-key", null, DRAIN_LAT, DRAIN_LNG)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_PHOTO);
        verifyNoInteractions(drainAiClassifier);
    }

    @Test
    void 점검_전후_사진이_같으면_DUPLICATE_PHOTO() {
        assertThatThrownBy(() -> service.record(USER_ID, ADOPTION_ID,
                request(ManagementType.REGULAR, null, ManagementActionType.CLEANING,
                        "same-key", "same-key", DRAIN_LAT, DRAIN_LNG)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_PHOTO);
        verifyNoInteractions(drainAiClassifier);
    }

    private ManagementLogRequest request(
            ManagementType type,
            Long requestId,
            ManagementActionType actionType,
            String beforePhotoKey,
            String afterPhotoKey,
            double latitude,
            double longitude) {
        return new ManagementLogRequest(type, requestId, actionType, beforePhotoKey,
                afterPhotoKey, "메모", latitude, longitude, true);
    }

    private void assessment(String photoKey, int dirtLevel, double confidence, int grade) {
        given(drainAiClassifier.assess(photoKey + "-url"))
                .willReturn(new DrainAiClassifier.Assessment(
                        dirtLevel, false, confidence, "판정", grade, false));
    }

    private InspectionRequest inspectionRequest() {
        InspectionRequest inspectionRequest = mock(InspectionRequest.class);
        lenient().when(inspectionRequest.getId()).thenReturn(REQUEST_ID);
        lenient().when(inspectionRequest.getAdoption()).thenReturn(adoption);
        lenient().when(inspectionRequestRepository.findById(REQUEST_ID))
                .thenReturn(Optional.of(inspectionRequest));
        return inspectionRequest;
    }
}
