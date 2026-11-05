package com.rainbutler.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.rainbutler.domain.adoption.entity.ManagementLog;
import com.rainbutler.domain.adoption.service.ManagementLogService;
import com.rainbutler.domain.cleaning.entity.Cleaning;
import com.rainbutler.domain.cleaning.service.CleaningService;
import com.rainbutler.domain.inspection.entity.Inspection;
import com.rainbutler.domain.inspection.entity.InspectionStatus;
import com.rainbutler.domain.inspection.service.InspectionService;
import com.rainbutler.domain.review.dto.ReviewDecisionRequest;
import com.rainbutler.domain.review.dto.ReviewDecisionResponse;
import com.rainbutler.domain.review.entity.ReviewDecision;
import com.rainbutler.domain.review.entity.ReviewTargetType;
import com.rainbutler.global.entity.ReviewStatus;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 관리자 검토 결정이 종류별 도메인 서비스로 전달되고, 응답 · 입력 검증이 맞는지 확인합니다.
 */
@ExtendWith(MockitoExtension.class)
class AdminReviewServiceTest {

    private static final Long ADMIN_ID = 99L;
    private static final Long TARGET_ID = 7L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 10, 0);

    @Mock
    private InspectionService inspectionService;
    @Mock
    private ManagementLogService managementLogService;
    @Mock
    private CleaningService cleaningService;

    private AdminReviewService service;

    @BeforeEach
    void setUp() {
        service = new AdminReviewService(inspectionService, managementLogService, cleaningService);
    }

    @Test
    void 점검을_승인하면_경험치와_승인_상태를_내려준다() {
        Inspection inspection = mock(Inspection.class);
        given(inspection.getId()).willReturn(TARGET_ID);
        given(inspection.getStatus()).willReturn(InspectionStatus.COMPLETED);
        given(inspection.getReviewedAt()).willReturn(NOW);
        given(inspection.getXpAwarded()).willReturn(15);
        given(inspectionService.approveReview(TARGET_ID, ADMIN_ID)).willReturn(inspection);

        ReviewDecisionResponse response = service.decide(
                ADMIN_ID, "INSPECTION", TARGET_ID,
                new ReviewDecisionRequest(ReviewDecision.APPROVE, null));

        assertThat(response.type()).isEqualTo(ReviewTargetType.INSPECTION);
        assertThat(response.id()).isEqualTo(TARGET_ID);
        assertThat(response.reviewStatus()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(response.reviewedAt()).isEqualTo(NOW);
        assertThat(response.xpAwarded()).isEqualTo(15);
    }

    @Test
    void 점검을_반려하면_반려_상태를_내려주고_경험치는_0이다() {
        Inspection inspection = mock(Inspection.class);
        given(inspection.getId()).willReturn(TARGET_ID);
        given(inspection.getStatus()).willReturn(InspectionStatus.REJECTED);
        given(inspection.getReviewedAt()).willReturn(NOW);
        given(inspection.getXpAwarded()).willReturn(0);
        given(inspectionService.rejectReview(TARGET_ID, ADMIN_ID)).willReturn(inspection);

        ReviewDecisionResponse response = service.decide(
                ADMIN_ID, "INSPECTION", TARGET_ID,
                new ReviewDecisionRequest(ReviewDecision.REJECT, "사진이 흐려요."));

        assertThat(response.reviewStatus()).isEqualTo(ReviewStatus.REJECTED);
        assertThat(response.xpAwarded()).isZero();
    }

    @Test
    void 관리_기록_승인은_관리_기록_서비스로_전달된다() {
        ManagementLog log = mock(ManagementLog.class);
        given(log.getId()).willReturn(TARGET_ID);
        given(log.getReviewStatus()).willReturn(ReviewStatus.APPROVED);
        given(log.getReviewedAt()).willReturn(NOW);
        given(managementLogService.approveReview(TARGET_ID, ADMIN_ID)).willReturn(log);

        ReviewDecisionResponse response = service.decide(
                ADMIN_ID, "management-log", TARGET_ID,
                new ReviewDecisionRequest(ReviewDecision.APPROVE, null));

        assertThat(response.type()).isEqualTo(ReviewTargetType.MANAGEMENT_LOG);
        assertThat(response.reviewStatus()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(response.xpAwarded()).isZero();
        verify(managementLogService).approveReview(TARGET_ID, ADMIN_ID);
    }

    @Test
    void 청소_반려는_청소_서비스로_전달된다() {
        Cleaning cleaning = mock(Cleaning.class);
        given(cleaning.getId()).willReturn(TARGET_ID);
        given(cleaning.getReviewStatus()).willReturn(ReviewStatus.REJECTED);
        given(cleaning.getReviewedAt()).willReturn(NOW);
        given(cleaningService.rejectReview(TARGET_ID, ADMIN_ID)).willReturn(cleaning);

        ReviewDecisionResponse response = service.decide(
                ADMIN_ID, "CLEANING", TARGET_ID,
                new ReviewDecisionRequest(ReviewDecision.REJECT, "현장 사진이 아니에요."));

        assertThat(response.type()).isEqualTo(ReviewTargetType.CLEANING);
        assertThat(response.reviewStatus()).isEqualTo(ReviewStatus.REJECTED);
    }

    @Test
    void 반려_사유가_없으면_INVALID_INPUT() {
        assertThatThrownBy(() -> service.decide(
                ADMIN_ID, "INSPECTION", TARGET_ID,
                new ReviewDecisionRequest(ReviewDecision.REJECT, "  ")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);

        verifyNoInteractions(inspectionService, managementLogService, cleaningService);
    }

    @Test
    void 지원하지_않는_종류면_INVALID_INPUT() {
        assertThatThrownBy(() -> service.decide(
                ADMIN_ID, "REPORT", TARGET_ID,
                new ReviewDecisionRequest(ReviewDecision.APPROVE, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);

        verifyNoInteractions(inspectionService, managementLogService, cleaningService);
    }
}
