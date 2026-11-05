package com.rainbutler.domain.review.service;

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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 사진 검토를 승인 · 반려합니다.
 *
 * <p>검토 대상 종류마다 후처리가 달라 각 도메인 서비스에 맡깁니다. 점검을 승인하면 인증을 통과한 점검과 같은
 * 후처리(관리 주기 · 빗물받이 사진 · 경험치 · 요청 완료)를 실행합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminReviewService {

    private final InspectionService inspectionService;
    private final ManagementLogService managementLogService;
    private final CleaningService cleaningService;

    /**
     * 검토 대기 기록을 승인하거나 반려합니다.
     *
     * @param adminId 검토한 관리자 id
     * @param type 검토 대상 종류 (INSPECTION · MANAGEMENT_LOG · CLEANING)
     * @param id 검토 대상 id
     * @param request 승인 · 반려 결정
     * @return 처리 결과
     * @throws BusinessException type · 결정이 잘못된 경우 (INVALID_INPUT), 반려 사유가 없는 경우
     *     (INVALID_INPUT), 대상이 없거나 (REVIEW_NOT_FOUND) 이미 처리된 경우 (REVIEW_ALREADY_PROCESSED)
     */
    @Transactional
    public ReviewDecisionResponse decide(
            Long adminId, String type, Long id, ReviewDecisionRequest request) {
        ReviewTargetType target = ReviewTargetType.parse(type);
        requireRejectReason(request);

        return switch (target) {
            case INSPECTION -> inspection(adminId, id, request.decision());
            case MANAGEMENT_LOG -> managementLog(adminId, id, request.decision());
            case CLEANING -> cleaning(adminId, id, request.decision());
        };
    }

    private ReviewDecisionResponse inspection(
            Long adminId, Long id, ReviewDecision decision) {
        Inspection inspection = decision == ReviewDecision.APPROVE
                ? inspectionService.approveReview(id, adminId)
                : inspectionService.rejectReview(id, adminId);
        return new ReviewDecisionResponse(
                ReviewTargetType.INSPECTION,
                inspection.getId(),
                inspection.getStatus() == InspectionStatus.COMPLETED
                        ? ReviewStatus.APPROVED
                        : ReviewStatus.REJECTED,
                inspection.getReviewedAt(),
                inspection.getXpAwarded());
    }

    private ReviewDecisionResponse managementLog(
            Long adminId, Long id, ReviewDecision decision) {
        ManagementLog log = decision == ReviewDecision.APPROVE
                ? managementLogService.approveReview(id, adminId)
                : managementLogService.rejectReview(id, adminId);
        return new ReviewDecisionResponse(
                ReviewTargetType.MANAGEMENT_LOG,
                log.getId(),
                log.getReviewStatus(),
                log.getReviewedAt(),
                0);
    }

    private ReviewDecisionResponse cleaning(
            Long adminId, Long id, ReviewDecision decision) {
        Cleaning cleaning = decision == ReviewDecision.APPROVE
                ? cleaningService.approveReview(id, adminId)
                : cleaningService.rejectReview(id, adminId);
        return new ReviewDecisionResponse(
                ReviewTargetType.CLEANING,
                cleaning.getId(),
                cleaning.getReviewStatus(),
                cleaning.getReviewedAt(),
                0);
    }

    private void requireRejectReason(ReviewDecisionRequest request) {
        if (request.decision() == ReviewDecision.REJECT
                && (request.reason() == null || request.reason().isBlank())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "반려 사유를 입력해 주세요.");
        }
    }
}
