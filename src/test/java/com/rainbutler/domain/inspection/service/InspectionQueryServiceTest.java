package com.rainbutler.domain.inspection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.rainbutler.domain.adoption.entity.Adoption;
import com.rainbutler.domain.adoption.entity.AdoptionStatus;
import com.rainbutler.domain.adoption.repository.AdoptionRepository;
import com.rainbutler.domain.inspection.dto.InspectionDetailResponse;
import com.rainbutler.domain.inspection.dto.InspectionListResponse;
import com.rainbutler.domain.inspection.entity.Inspection;
import com.rainbutler.domain.inspection.entity.InspectionKind;
import com.rainbutler.domain.inspection.entity.InspectionStatus;
import com.rainbutler.domain.inspection.repository.InspectionRepository;
import com.rainbutler.domain.drain.entity.Drain;
import com.rainbutler.domain.user.entity.Role;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.global.config.PolicyProperties;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.storage.StorageService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InspectionQueryServiceTest {

    @Mock
    private InspectionRepository inspectionRepository;

    @Mock
    private AdoptionRepository adoptionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private PolicyProperties policyProperties;

    @Mock
    private Clock clock;

    @Mock
    private Drain drain;

    @Mock
    private Adoption adoption;

    @InjectMocks
    private InspectionQueryService inspectionQueryService;

    private User user;
    private Inspection inspection;

    @BeforeEach
    void setUp() {
        user = User.local(UUID.randomUUID() + "@test.com", "{noop}x", "테스트");
        ReflectionTestUtils.setField(user, "id", 1L);

        LocalDateTime now = LocalDateTime.now();
        Clock fixedClock = Clock.fixed(now.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());

        inspection = Inspection.builder()
                .user(user)
                .drain(drain)
                .adoption(adoption)
                .kind(InspectionKind.ROUTINE)
                .beforePhotoKey("before-key")
                .beforeGrade(3)
                .aiConfidence(BigDecimal.valueOf(0.9))
                .distanceM(5)
                .capturedAt(now)
                .note("점검했어요")
                .build();
        ReflectionTestUtils.setField(inspection, "id", 1L);
        ReflectionTestUtils.setField(inspection, "status", InspectionStatus.COMPLETED);
        ReflectionTestUtils.setField(inspection, "afterPhotoKey", "after-key");
        ReflectionTestUtils.setField(inspection, "afterGrade", 1);
        ReflectionTestUtils.setField(inspection, "afterConfidence", BigDecimal.valueOf(0.9));
        ReflectionTestUtils.setField(inspection, "xpAwarded", 5);
        ReflectionTestUtils.setField(inspection, "rewarded", true);

        lenient().when(drain.getId()).thenReturn(1L);
        lenient().when(adoption.getId()).thenReturn(1L);
        lenient().when(adoption.getUser()).thenReturn(user);
        lenient().when(adoption.getDrain()).thenReturn(drain);
        lenient().when(adoption.getNickname()).thenReturn("또랑이");
        lenient().when(adoption.getStatus()).thenReturn(AdoptionStatus.ACTIVE);

        lenient().when(clock.getZone()).thenReturn(ZoneId.systemDefault());
        lenient().when(clock.instant()).thenReturn(fixedClock.instant());
        lenient().when(policyProperties.photoRetentionDays()).thenReturn(365);
        lenient().when(policyProperties.locationMismatchMeters()).thenReturn(50);
        lenient().when(policyProperties.aiConfidenceThreshold()).thenReturn(BigDecimal.valueOf(0.8));
        lenient().when(storageService.toReadUrl(any())).thenAnswer(inv -> "https://s3.example.com/" + inv.getArgument(0));
    }

    @Test
    void 점검_기록_상세를_조회한다() {
        when(userRepository.findById(1L)).thenReturn(java.util.Optional.of(user));
        when(inspectionRepository.findById(1L)).thenReturn(java.util.Optional.of(inspection));

        InspectionDetailResponse response = inspectionQueryService.findDetail(1L, 1L);

        assertThat(response.inspectionId()).isEqualTo(1L);
        assertThat(response.drain().drainId()).isEqualTo(1L);
        assertThat(response.drain().name()).isEqualTo("또랑이");
        assertThat(response.kind()).isEqualTo(InspectionKind.ROUTINE);
        assertThat(response.review().name()).isEqualTo("NOT_REQUIRED");
        assertThat(response.beforePhotoUrl()).isNotNull();
        assertThat(response.afterPhotoUrl()).isNotNull();
        assertThat(response.beforeGrade()).isEqualTo(3);
        assertThat(response.afterGrade()).isEqualTo(1);
        assertThat(response.cleaned()).isTrue();
        assertThat(response.distanceM()).isEqualTo(5);
        assertThat(response.xp()).isEqualTo(5);
        assertThat(response.note()).isEqualTo("점검했어요");
        assertThat(response.photoDeleted()).isFalse();
    }

    @Test
    void 다른_사람의_점검_기록이면_FORBIDDEN() {
        User other = User.local(UUID.randomUUID() + "@test.com", "{noop}x", "다른집사");
        ReflectionTestUtils.setField(other, "id", 2L);

        when(userRepository.findById(2L)).thenReturn(java.util.Optional.of(other));
        when(inspectionRepository.findById(1L)).thenReturn(java.util.Optional.of(inspection));

        assertThatThrownBy(() -> inspectionQueryService.findDetail(2L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void 없는_사용자이면_USER_NOT_FOUND() {
        when(userRepository.findById(999L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> inspectionQueryService.findDetail(999L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void 없는_점검_기록이면_INSPECTION_NOT_FOUND() {
        when(userRepository.findById(1L)).thenReturn(java.util.Optional.of(user));
        when(inspectionRepository.findById(999L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> inspectionQueryService.findDetail(1L, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INSPECTION_NOT_FOUND);
    }

    @Test
    void 입양별_점검_기록을_조회한다() {
        when(userRepository.findById(1L)).thenReturn(java.util.Optional.of(user));
        when(adoptionRepository.findById(1L)).thenReturn(java.util.Optional.of(adoption));
        when(inspectionRepository.findPageByAdoptionId(
                1L, Long.MAX_VALUE, PageRequest.of(0, 21)))
                .thenReturn(List.of(inspection));

        InspectionListResponse response = inspectionQueryService.findByAdoption(1L, 1L, null, null);

        assertThat(response.inspections()).hasSize(1);
        assertThat(response.inspections().get(0).inspectionId()).isEqualTo(1L);
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void 다른_사람의_입양_이력이면_FORBIDDEN() {
        User other = User.local(UUID.randomUUID() + "@test.com", "{noop}x", "다른집사");
        ReflectionTestUtils.setField(other, "id", 2L);

        when(userRepository.findById(2L)).thenReturn(java.util.Optional.of(other));
        when(adoptionRepository.findById(1L)).thenReturn(java.util.Optional.of(adoption));

        assertThatThrownBy(() -> inspectionQueryService.findByAdoption(2L, 1L, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void 없는_입양이면_ADOPTION_NOT_FOUND() {
        when(userRepository.findById(1L)).thenReturn(java.util.Optional.of(user));
        when(adoptionRepository.findById(999L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> inspectionQueryService.findByAdoption(1L, 999L, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ADOPTION_NOT_FOUND);
    }

    @Test
    void size가_0보다_작으면_INVALID_INPUT() {
        lenient().when(userRepository.findById(1L)).thenReturn(java.util.Optional.of(user));
        lenient().when(adoptionRepository.findById(1L)).thenReturn(java.util.Optional.of(adoption));

        assertThatThrownBy(() -> inspectionQueryService.findByAdoption(1L, 1L, null, 0))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void cursor가_0보다_작으면_INVALID_INPUT() {
        lenient().when(userRepository.findById(1L)).thenReturn(java.util.Optional.of(user));
        lenient().when(adoptionRepository.findById(1L)).thenReturn(java.util.Optional.of(adoption));

        assertThatThrownBy(() -> inspectionQueryService.findByAdoption(1L, 1L, 0L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }
}
