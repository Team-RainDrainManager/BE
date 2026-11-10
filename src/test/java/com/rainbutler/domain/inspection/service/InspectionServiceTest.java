package com.rainbutler.domain.inspection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.rainbutler.domain.adoption.entity.Adoption;
import com.rainbutler.domain.adoption.entity.AdoptionStatus;
import com.rainbutler.domain.adoption.repository.AdoptionRepository;
import com.rainbutler.domain.adoption.service.ManagementCyclePolicy;
import com.rainbutler.domain.drain.entity.Drain;
import com.rainbutler.domain.drain.entity.DrainStatus;
import com.rainbutler.domain.drain.repository.DrainRepository;
import com.rainbutler.domain.drain.service.DrainAiClassifier;
import com.rainbutler.domain.drain.service.GrowthLevelPolicy;
import com.rainbutler.domain.inspection.dto.InspectionCompleteRequest;
import com.rainbutler.domain.inspection.dto.InspectionEligibilityResponse;
import com.rainbutler.domain.inspection.dto.InspectionStartRequest;
import com.rainbutler.domain.inspection.dto.InspectionSubmitResponse;
import com.rainbutler.domain.inspection.entity.Inspection;
import com.rainbutler.domain.inspection.entity.InspectionKind;
import com.rainbutler.domain.inspection.entity.InspectionStatus;
import com.rainbutler.domain.inspection.repository.InspectionRepository;
import com.rainbutler.domain.point.repository.PointHistoryRepository;
import com.rainbutler.domain.request.entity.InspectionRequest;
import com.rainbutler.domain.request.entity.RequestStatus;
import com.rainbutler.domain.request.repository.InspectionRequestRepository;
import com.rainbutler.domain.request.service.InspectionRequestService;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.global.config.PolicyProperties;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.storage.StorageService;
import com.rainbutler.global.storage.UploadPurpose;
import com.rainbutler.support.TestPolicies;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 점검 · 청소 기록 공통 제출의 상태 결정 · 후처리 · 권한 재검증을 확인합니다.
 */
@ExtendWith(MockitoExtension.class)
class InspectionServiceTest {

    private static final Long USER_ID = 3L;
    private static final Long DRAIN_ID = 10L;
    private static final Long ADOPTION_ID = 15L;
    private static final Long REQUEST_ID = 20L;
    private static final Long INSPECTION_ID = 30L;
    private static final double DRAIN_LAT = 37.5;
    private static final double DRAIN_LNG = 127.0;
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-02T01:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    private static final OffsetDateTime CAPTURED_AT = OffsetDateTime.now(CLOCK);
    private static final PolicyProperties POLICIES = TestPolicies.defaults();

    @Mock
    private InspectionRepository inspectionRepository;
    @Mock
    private DrainRepository drainRepository;
    @Mock
    private AdoptionRepository adoptionRepository;
    @Mock
    private InspectionRequestRepository inspectionRequestRepository;
    @Mock
    private PointHistoryRepository pointHistoryRepository;
    @Mock
    private InspectionEligibilityService inspectionEligibilityService;
    @Mock
    private InspectionRequestService inspectionRequestService;
    @Mock
    private StorageService storageService;
    @Mock
    private DrainAiClassifier drainAiClassifier;
    @Mock
    private UserRepository userRepository;

    private InspectionService service;
    private User user;
    private Drain drain;
    private Adoption adoption;

    @BeforeEach
    void setUp() {
        InspectionRewardService rewardService = new InspectionRewardService(
                inspectionRepository, pointHistoryRepository, new GrowthLevelPolicy(), POLICIES);
        service = new InspectionService(inspectionRepository, drainRepository, adoptionRepository,
                inspectionRequestRepository, inspectionEligibilityService,
                inspectionRequestService, rewardService, storageService, drainAiClassifier,
                new ManagementCyclePolicy(POLICIES), POLICIES, userRepository, CLOCK);

        user = User.local("a@test.com", "{noop}x", "집사");
        ReflectionTestUtils.setField(user, "id", USER_ID);

        drain = Drain.builder()
                .registeredBy(user)
                .latitude(DRAIN_LAT)
                .longitude(DRAIN_LNG)
                .photoUrl("drain-key")
                .dirtLevel(2)
                .status(DrainStatus.ACTIVE)
                .isRisk(false)
                .build();
        ReflectionTestUtils.setField(drain, "id", DRAIN_ID);

        adoption = Adoption.builder()
                .user(user)
                .drain(drain)
                .nickname("또랑")
                .adoptedAt(NOW.minusDays(3))
                .build();
        ReflectionTestUtils.setField(adoption, "id", ADOPTION_ID);

        lenient().when(drainRepository.findById(DRAIN_ID)).thenReturn(Optional.of(drain));
        lenient().when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        lenient().when(adoptionRepository.findByDrain_IdAndStatus(DRAIN_ID, AdoptionStatus.ACTIVE))
                .thenReturn(Optional.of(adoption));
        lenient().when(inspectionEligibilityService.check(any(), any(), any()))
                .thenReturn(InspectionEligibilityResponse.allowed());
        lenient().when(storageService.verifyUploaded(any(), anyString(), any(UploadPurpose.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));
        lenient().when(storageService.toReadUrl(anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0) + "-url");
        lenient().when(inspectionRepository.save(any(Inspection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(inspectionRepository.countRewardedBetween(any(), any(), any(), any()))
                .thenReturn(0L);
    }

    @Test
    void 깨끗한_점검_전_사진이면_청소_없이_완료하고_관리_주기와_경험치를_반영한다() {
        assessment("before-key", 0, 0.9);

        InspectionSubmitResponse response = service.start(
                USER_ID, startRequest(null, InspectionKind.ROUTINE, "before-key",
                        DRAIN_LAT, DRAIN_LNG));

        assertThat(response.status()).isEqualTo(InspectionStatus.COMPLETED);
        assertThat(response.beforeGrade()).isZero();
        assertThat(response.afterGrade()).isNull();
        assertThat(response.xpAwarded()).isEqualTo(POLICIES.inspectionXp());
        assertThat(drain.getPhotoUrl()).isEqualTo("before-key");
        assertThat(drain.getDirtLevel()).isEqualTo(1);
        assertThat(adoption.getScore()).isEqualTo(POLICIES.inspectionXp());
        assertThat(adoption.getLastManagedAt()).isEqualTo(NOW);
        assertThat(adoption.getNextManagementAt())
                .isEqualTo(NOW.plusDays(POLICIES.managementCycleDays()));
        assertThat(user.getPoint()).isEqualTo(POLICIES.inspectionXp());
        verify(pointHistoryRepository).save(any());
    }

    @Test
    void 더러운_점검_전_사진이면_청소_후_사진을_기다린다() {
        assessment("before-key", 2, 0.9);

        InspectionSubmitResponse response = service.start(
                USER_ID, startRequest(null, InspectionKind.ROUTINE, "before-key",
                        DRAIN_LAT, DRAIN_LNG));

        assertThat(response.status()).isEqualTo(InspectionStatus.NEEDS_CLEANING);
        assertThat(response.xpAwarded()).isZero();
        assertThat(drain.getPhotoUrl()).isEqualTo("drain-key");
        assertThat(adoption.getScore()).isZero();
        assertThat(adoption.getLastManagedAt()).isNull();
        verify(inspectionRequestService, never()).complete(any(), any(), any());
    }

    @Test
    void 청소_후_등급이_좋아지면_완료하고_청소_후_사진을_반영한다() {
        assessment("before-key", 2, 0.9);
        Inspection inspection = startAndCapture(
                USER_ID, startRequest(null, InspectionKind.ROUTINE, "before-key",
                        DRAIN_LAT, DRAIN_LNG));
        assessment("after-key", 1, 0.9);

        InspectionSubmitResponse response = service.complete(
                USER_ID, INSPECTION_ID, new InspectionCompleteRequest("after-key", true));

        assertThat(response.status()).isEqualTo(InspectionStatus.COMPLETED);
        assertThat(response.afterGrade()).isEqualTo(1);
        assertThat(response.xpAwarded()).isEqualTo(POLICIES.inspectionXp());
        assertThat(inspection.getAfterPhotoKey()).isEqualTo("after-key");
        assertThat(drain.getPhotoUrl()).isEqualTo("after-key");
        assertThat(drain.getDirtLevel()).isEqualTo(2);
        assertThat(adoption.getLastManagedAt()).isEqualTo(NOW);
    }

    @Test
    void 막힘에서_쌓임으로_좋아지면_더러움을_2단계로_반영한다() {
        assessment("before-key", DrainAiClassifier.GRADE_BLOCKED, 0.9);
        startAndCapture(USER_ID, startRequest(null, InspectionKind.ROUTINE, "before-key",
                DRAIN_LAT, DRAIN_LNG));
        assessment("after-key", DrainAiClassifier.GRADE_PILED, 0.9);

        InspectionSubmitResponse response = service.complete(
                USER_ID, INSPECTION_ID, new InspectionCompleteRequest("after-key", true));

        assertThat(response.status()).isEqualTo(InspectionStatus.COMPLETED);
        assertThat(drain.getDirtLevel()).isEqualTo(2);
    }

    @Test
    void 청소_후_등급이_좋아지지_않으면_다시_청소를_기다린다() {
        assessment("before-key", 2, 0.9);
        startAndCapture(USER_ID, startRequest(null, InspectionKind.ROUTINE, "before-key",
                DRAIN_LAT, DRAIN_LNG));
        assessment("after-key", 2, 0.9);

        InspectionSubmitResponse response = service.complete(
                USER_ID, INSPECTION_ID, new InspectionCompleteRequest("after-key", true));

        assertThat(response.status()).isEqualTo(InspectionStatus.NEEDS_CLEANING);
        assertThat(response.afterGrade()).isEqualTo(2);
        assertThat(response.xpAwarded()).isZero();
        assertThat(drain.getPhotoUrl()).isEqualTo("drain-key");
        assertThat(adoption.getLastManagedAt()).isNull();
    }

    @Test
    void 위치가_멀면_기록을_남기지_않고_다시_찍게_한다() {
        assertThatThrownBy(() -> service.start(
                USER_ID, startRequest(null, InspectionKind.ROUTINE, "before-key",
                        DRAIN_LAT + 0.01, DRAIN_LNG)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INSPECTION_LOCATION_MISMATCH);

        verify(inspectionRepository, never()).save(any());
        verifyNoInteractions(drainAiClassifier);
    }

    @Test
    void 찍은_지_오래된_사진은_기록을_남기지_않고_다시_찍게_한다() {
        InspectionStartRequest request = new InspectionStartRequest(
                DRAIN_ID, InspectionKind.ROUTINE, null, "before-key", DRAIN_LAT, DRAIN_LNG,
                CAPTURED_AT.minusMinutes(POLICIES.captureMaxAgeMinutes() + 1L), null, true);

        assertThatThrownBy(() -> service.start(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INSPECTION_PHOTO_TOO_OLD);

        verify(inspectionRepository, never()).save(any());
        verifyNoInteractions(drainAiClassifier);
    }

    @Test
    void 촬영_시각은_앱이_보낸_시간대와_상관없이_서버_시간으로_저장한다() {
        assessment("before-key", 0, 0.9);
        InspectionStartRequest request = new InspectionStartRequest(
                DRAIN_ID, InspectionKind.ROUTINE, null, "before-key", DRAIN_LAT, DRAIN_LNG,
                OffsetDateTime.parse("2026-11-02T00:50:00Z"), null, true);

        service.start(USER_ID, request);

        ArgumentCaptor<Inspection> captor = ArgumentCaptor.forClass(Inspection.class);
        verify(inspectionRepository).save(captor.capture());
        assertThat(captor.getValue().getCapturedAt()).isEqualTo(NOW.minusMinutes(10));
    }

    @Test
    void AI_확신도가_낮으면_검토_대기로_남긴다() {
        assessment("before-key", 0, 0.5);

        InspectionSubmitResponse response = service.start(
                USER_ID, startRequest(null, InspectionKind.ROUTINE, "before-key",
                        DRAIN_LAT, DRAIN_LNG));

        assertThat(response.status()).isEqualTo(InspectionStatus.PENDING);
        assertThat(adoption.getLastManagedAt()).isNull();
    }

    @Test
    void 비_예보_점검을_마치면_요청을_완료하고_무응답_횟수를_초기화한다() {
        given(inspectionRequestRepository.findById(REQUEST_ID))
                .willReturn(Optional.of(request(RequestStatus.ACCEPTED)));
        assessment("before-key", 0, 0.9);

        InspectionSubmitResponse response = service.start(
                USER_ID, startRequest(REQUEST_ID, InspectionKind.FORECAST, "before-key",
                        DRAIN_LAT, DRAIN_LNG));

        assertThat(response.status()).isEqualTo(InspectionStatus.COMPLETED);
        verify(inspectionRequestService).complete(REQUEST_ID, USER_ID, NOW);
        assertThat(adoption.getIgnoreCount()).isZero();
    }

    @Test
    void 같은_요청으로_두_번_제출할_수_없다() {
        given(inspectionRequestRepository.findById(REQUEST_ID))
                .willReturn(Optional.of(request(RequestStatus.ACCEPTED)));
        given(inspectionRepository.existsByRequest_Id(REQUEST_ID)).willReturn(true);

        assertThatThrownBy(() -> service.start(
                USER_ID, startRequest(REQUEST_ID, InspectionKind.FORECAST, "before-key",
                        DRAIN_LAT, DRAIN_LNG)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
    }

    @Test
    void 비_예보_요청_완료_마감이_지나면_청소_후_사진을_제출할_수_없다() {
        InspectionRequest request = request(RequestStatus.ACCEPTED);
        ReflectionTestUtils.setField(request, "completeDeadlineAt", NOW.minusMinutes(1));
        given(inspectionRequestRepository.findById(REQUEST_ID)).willReturn(Optional.of(request));
        assessment("before-key", 2, 0.9);
        startAndCapture(USER_ID, startRequest(REQUEST_ID, InspectionKind.FORECAST, "before-key",
                DRAIN_LAT, DRAIN_LNG));

        assertThatThrownBy(() -> service.complete(
                USER_ID, INSPECTION_ID, new InspectionCompleteRequest("after-key", true)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REQUEST_NOT_COMPLETABLE);
    }

    @Test
    void 정기_점검에는_요청_id를_보낼_수_없다() {
        assertThatThrownBy(() -> service.start(
                USER_ID, startRequest(REQUEST_ID, InspectionKind.ROUTINE, "before-key",
                        DRAIN_LAT, DRAIN_LNG)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void 안전_수칙을_확인하지_않으면_제출할_수_없다() {
        InspectionStartRequest request = new InspectionStartRequest(
                DRAIN_ID, InspectionKind.ROUTINE, null, "before-key", DRAIN_LAT, DRAIN_LNG,
                CAPTURED_AT, null, false);

        assertThatThrownBy(() -> service.start(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void 다른_사람의_점검은_마무리할_수_없다() {
        User other = User.local("b@test.com", "{noop}x", "다른 집사");
        ReflectionTestUtils.setField(other, "id", USER_ID + 1);
        Inspection inspection = Inspection.builder()
                .user(other)
                .drain(drain)
                .adoption(adoption)
                .kind(InspectionKind.ROUTINE)
                .beforePhotoKey("before-key")
                .beforeGrade(2)
                .aiConfidence(java.math.BigDecimal.valueOf(0.9))
                .distanceM(1)
                .capturedAt(NOW)
                .build();
        ReflectionTestUtils.setField(inspection, "id", INSPECTION_ID);
        given(inspectionRepository.findById(INSPECTION_ID))
                .willReturn(Optional.of(inspection));

        assertThatThrownBy(() -> service.complete(
                USER_ID, INSPECTION_ID, new InspectionCompleteRequest("after-key", true)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void 이미_완료된_점검은_다시_마무리할_수_없다() {
        Inspection inspection = Inspection.builder()
                .user(user)
                .drain(drain)
                .adoption(adoption)
                .kind(InspectionKind.ROUTINE)
                .beforePhotoKey("before-key")
                .beforeGrade(0)
                .aiConfidence(java.math.BigDecimal.valueOf(0.9))
                .distanceM(1)
                .capturedAt(NOW)
                .build();
        inspection.completeWithoutCleaning();
        ReflectionTestUtils.setField(inspection, "id", INSPECTION_ID);
        given(inspectionRepository.findById(INSPECTION_ID))
                .willReturn(Optional.of(inspection));

        assertThatThrownBy(() -> service.complete(
                USER_ID, INSPECTION_ID, new InspectionCompleteRequest("after-key", true)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INSPECTION_ALREADY_COMPLETED);
    }

    private Inspection startAndCapture(
            Long userId, InspectionStartRequest request) {
        service.start(userId, request);
        ArgumentCaptor<Inspection> captor = ArgumentCaptor.forClass(Inspection.class);
        verify(inspectionRepository).save(captor.capture());
        Inspection inspection = captor.getValue();
        ReflectionTestUtils.setField(inspection, "id", INSPECTION_ID);
        given(inspectionRepository.findById(INSPECTION_ID))
                .willReturn(Optional.of(inspection));
        return inspection;
    }

    private InspectionStartRequest startRequest(
            Long requestId, InspectionKind kind, String beforeKey, double latitude,
            double longitude) {
        return new InspectionStartRequest(
                DRAIN_ID, kind, requestId, beforeKey, latitude, longitude, CAPTURED_AT, "메모", true);
    }

    private InspectionRequest request(RequestStatus status) {
        InspectionRequest request = InspectionRequest.builder()
                .drain(drain)
                .adoption(adoption)
                .status(status)
                .sentAt(NOW.minusHours(1))
                .replyDeadlineAt(NOW.plusHours(1))
                .completeDeadlineAt(NOW.plusHours(5))
                .build();
        ReflectionTestUtils.setField(request, "id", REQUEST_ID);
        return request;
    }

    private void assessment(String key, int grade, double confidence) {
        given(drainAiClassifier.assess(key + "-url"))
                .willReturn(new DrainAiClassifier.Assessment(
                        DrainAiClassifier.toDirtLevel(grade), grade >= 3, confidence, "판정", grade, false));
    }
}
