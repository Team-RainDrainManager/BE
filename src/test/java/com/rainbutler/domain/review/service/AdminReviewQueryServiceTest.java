package com.rainbutler.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.rainbutler.domain.adoption.entity.Adoption;
import com.rainbutler.domain.adoption.entity.ManagementLog;
import com.rainbutler.domain.adoption.repository.ManagementLogRepository;
import com.rainbutler.domain.cleaning.entity.Cleaning;
import com.rainbutler.domain.cleaning.repository.CleaningRepository;
import com.rainbutler.domain.drain.entity.Drain;
import com.rainbutler.domain.inspection.entity.Inspection;
import com.rainbutler.domain.inspection.repository.InspectionRepository;
import com.rainbutler.domain.review.dto.ReviewItemResponse;
import com.rainbutler.domain.review.dto.ReviewListResponse;
import com.rainbutler.domain.review.entity.ReviewTargetType;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.storage.StorageService;
import com.rainbutler.support.TestPolicies;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 검토 대기 목록이 종류별로 모여 최신순으로 정렬되고, 종류 필터 · 개수 제한 · 입력 검증이 맞는지 확인합니다.
 */
@ExtendWith(MockitoExtension.class)
class AdminReviewQueryServiceTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 11, 5, 9, 0);

    @Mock
    private InspectionRepository inspectionRepository;
    @Mock
    private ManagementLogRepository managementLogRepository;
    @Mock
    private CleaningRepository cleaningRepository;
    @Mock
    private StorageService storageService;

    private AdminReviewQueryService service;

    @BeforeEach
    void setUp() {
        service = new AdminReviewQueryService(
                inspectionRepository, managementLogRepository, cleaningRepository,
                storageService, TestPolicies.defaults());
    }

    @Test
    void 전체_조회는_세_종류를_최신순으로_합친다() {
        Inspection inspection = inspection(1L, BASE.plusMinutes(10));
        ManagementLog log = managementLog(2L, BASE.plusMinutes(20));
        Cleaning cleaning = cleaning(3L, BASE);
        given(inspectionRepository.findPendingReviews(any()))
                .willReturn(List.of(inspection));
        given(managementLogRepository.findPendingReviews(any()))
                .willReturn(List.of(log));
        given(cleaningRepository.findPendingReviews(any()))
                .willReturn(List.of(cleaning));

        ReviewListResponse response = service.findPending(null, null);

        assertThat(response.reviews())
                .extracting(ReviewItemResponse::type)
                .containsExactly(
                        ReviewTargetType.MANAGEMENT_LOG,
                        ReviewTargetType.INSPECTION,
                        ReviewTargetType.CLEANING);
        assertThat(response.reviews()).extracting(ReviewItemResponse::id)
                .containsExactly(2L, 1L, 3L);
    }

    @Test
    void 종류를_지정하면_그_저장소만_조회한다() {
        Inspection inspection = inspection(1L, BASE);
        given(inspectionRepository.findPendingReviews(any()))
                .willReturn(List.of(inspection));

        ReviewListResponse response = service.findPending("INSPECTION", 20);

        assertThat(response.reviews()).hasSize(1);
        assertThat(response.reviews().get(0).type()).isEqualTo(ReviewTargetType.INSPECTION);
        verifyNoInteractions(managementLogRepository, cleaningRepository);
    }

    @Test
    void limit_개수만큼만_최신순으로_내려준다() {
        List<Inspection> inspections = List.of(
                inspection(1L, BASE),
                inspection(2L, BASE.plusMinutes(10)),
                inspection(3L, BASE.plusMinutes(20)));
        given(inspectionRepository.findPendingReviews(any()))
                .willReturn(inspections);

        ReviewListResponse response = service.findPending("INSPECTION", 2);

        assertThat(response.reviews()).extracting(ReviewItemResponse::id)
                .containsExactly(3L, 2L);
    }

    @Test
    void 점검은_종류에_맞는_값과_검토_사유를_내려준다() {
        Inspection inspection = inspection(1L, BASE);
        given(inspectionRepository.findPendingReviews(any()))
                .willReturn(List.of(inspection));
        given(storageService.toReadUrl("before-1")).willReturn("http://before-1");

        ReviewItemResponse item = service.findPending("INSPECTION", 20).reviews().get(0);

        assertThat(item.drainName()).isEqualTo("역곡역 앞");
        assertThat(item.beforePhotoUrl()).isEqualTo("http://before-1");
        assertThat(item.afterPhotoUrl()).isNull();
        assertThat(item.beforeGrade()).isEqualTo(2);
        assertThat(item.afterGrade()).isNull();
        assertThat(item.distanceM()).isEqualTo(80);
        assertThat(item.reason()).contains("80m 떨어져").contains("확신도가 낮아요");
    }

    @Test
    void 검토_사유가_없으면_기본_문구를_내려준다() {
        Cleaning cleaning = cleaning(3L, BASE);
        given(cleaningRepository.findPendingReviews(any()))
                .willReturn(List.of(cleaning));

        ReviewItemResponse item = service.findPending("CLEANING", 20).reviews().get(0);

        assertThat(item.reason()).isEqualTo("관리자 검토가 필요해요.");
    }

    @Test
    void limit이_1보다_작으면_INVALID_INPUT() {
        assertThatThrownBy(() -> service.findPending("INSPECTION", 0))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);

        verifyNoInteractions(inspectionRepository, managementLogRepository, cleaningRepository);
    }

    @Test
    void 지원하지_않는_종류면_INVALID_INPUT() {
        assertThatThrownBy(() -> service.findPending("REPORT", 20))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);

        verifyNoInteractions(inspectionRepository, managementLogRepository, cleaningRepository);
    }

    private Inspection inspection(Long id, LocalDateTime createdAt) {
        User user = user();
        Drain drain = drain();
        given(drain.getName()).willReturn("역곡역 앞");
        Inspection inspection = mock(Inspection.class);
        given(inspection.getId()).willReturn(id);
        given(inspection.getCreatedAt()).willReturn(createdAt);
        given(inspection.getUser()).willReturn(user);
        given(inspection.getDrain()).willReturn(drain);
        given(inspection.getAdoption()).willReturn(null);
        given(inspection.getBeforePhotoKey()).willReturn("before-" + id);
        given(inspection.getAfterPhotoKey()).willReturn(null);
        given(inspection.getBeforeGrade()).willReturn(2);
        given(inspection.getAfterGrade()).willReturn(null);
        given(inspection.getAiConfidence()).willReturn(new BigDecimal("0.40"));
        given(inspection.getAfterConfidence()).willReturn(null);
        given(inspection.getDistanceM()).willReturn(80);
        return inspection;
    }

    private ManagementLog managementLog(Long id, LocalDateTime createdAt) {
        User user = user();
        Drain drain = drain();
        Adoption adoption = mock(Adoption.class);
        given(adoption.getNickname()).willReturn("내 또랑이");
        given(adoption.getUser()).willReturn(user);
        given(adoption.getDrain()).willReturn(drain);

        ManagementLog log = mock(ManagementLog.class);
        given(log.getId()).willReturn(id);
        given(log.getCreatedAt()).willReturn(createdAt);
        given(log.getAdoption()).willReturn(adoption);
        given(log.getBeforePhotoUrl()).willReturn("log-before-" + id);
        given(log.getAfterPhotoUrl()).willReturn(null);
        given(log.getDirtLevel()).willReturn(3);
        given(log.getAiConfidence()).willReturn(new BigDecimal("0.40"));
        given(log.isLocationMismatch()).willReturn(false);
        return log;
    }

    private Cleaning cleaning(Long id, LocalDateTime createdAt) {
        User user = user();
        Drain drain = drain();
        given(drain.getName()).willReturn("역곡역 앞");
        Cleaning cleaning = mock(Cleaning.class);
        given(cleaning.getId()).willReturn(id);
        given(cleaning.getCreatedAt()).willReturn(createdAt);
        given(cleaning.getUser()).willReturn(user);
        given(cleaning.getDrain()).willReturn(drain);
        given(cleaning.getBeforePhotoUrl()).willReturn("clean-before-" + id);
        given(cleaning.getAfterPhotoUrl()).willReturn(null);
        given(cleaning.getDirtLevel()).willReturn(2);
        given(cleaning.getAfterGrade()).willReturn(1);
        given(cleaning.getDirtConfidence()).willReturn(new BigDecimal("0.90"));
        given(cleaning.isLocationMismatch()).willReturn(false);
        return cleaning;
    }

    private User user() {
        User user = mock(User.class);
        given(user.getId()).willReturn(1L);
        given(user.getNickname()).willReturn("나");
        return user;
    }

    private Drain drain() {
        Drain drain = mock(Drain.class);
        given(drain.getId()).willReturn(10L);
        return drain;
    }
}
