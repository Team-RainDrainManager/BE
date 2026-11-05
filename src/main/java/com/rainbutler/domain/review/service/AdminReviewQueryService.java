package com.rainbutler.domain.review.service;

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
import com.rainbutler.global.config.PolicyProperties;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.storage.StorageService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 사진 검토 대기 목록을 조회합니다.
 *
 * <p>위치 불일치 · 낮은 AI 확신도로 {@code PENDING}이 된 점검 · 관리 기록 · 청소를 한 목록으로 모읍니다.
 * 응답이 같은 모양이라 관리자는 종류와 상관없이 사진을 보고 승인 · 반려할 수 있습니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminReviewQueryService {

    /** 한 번에 내려주는 기본 개수 */
    public static final int DEFAULT_LIMIT = 20;
    /** 한 번에 내려주는 최대 개수 (넘으면 이 값으로 줄인다) */
    public static final int MAX_LIMIT = 100;

    private final InspectionRepository inspectionRepository;
    private final ManagementLogRepository managementLogRepository;
    private final CleaningRepository cleaningRepository;
    private final StorageService storageService;
    private final PolicyProperties policyProperties;

    /**
     * 검토 대기 목록을 최신순으로 조회합니다.
     *
     * @param type 검토 대상 종류 (null · 비어 있으면 전체)
     * @param limit 가져올 개수 (null이면 20, 100 초과면 100)
     * @return 검토 대기 목록
     * @throws BusinessException type · limit이 잘못된 경우 (INVALID_INPUT)
     */
    public ReviewListResponse findPending(String type, Integer limit) {
        int pageSize = limit == null ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        if (pageSize < 1) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "limit은 1 이상이어야 해요.");
        }
        ReviewTargetType target = type == null || type.isBlank()
                ? null
                : ReviewTargetType.parse(type);
        PageRequest pageable = PageRequest.of(0, pageSize);

        List<ReviewItemResponse> reviews = new ArrayList<>();
        if (target == null || target == ReviewTargetType.INSPECTION) {
            inspectionRepository.findPendingReviews(pageable).stream()
                    .map(this::inspection)
                    .forEach(reviews::add);
        }
        if (target == null || target == ReviewTargetType.MANAGEMENT_LOG) {
            managementLogRepository.findPendingReviews(pageable).stream()
                    .map(this::managementLog)
                    .forEach(reviews::add);
        }
        if (target == null || target == ReviewTargetType.CLEANING) {
            cleaningRepository.findPendingReviews(pageable).stream()
                    .map(this::cleaning)
                    .forEach(reviews::add);
        }

        List<ReviewItemResponse> page = reviews.stream()
                .sorted(Comparator.comparing(ReviewItemResponse::createdAt)
                        .thenComparing(ReviewItemResponse::id)
                        .reversed())
                .limit(pageSize)
                .toList();
        return new ReviewListResponse(page);
    }

    private ReviewItemResponse inspection(Inspection inspection) {
        Adoption adoption = inspection.getAdoption();
        return new ReviewItemResponse(
                ReviewTargetType.INSPECTION,
                inspection.getId(),
                inspection.getCreatedAt(),
                inspection.getUser().getId(),
                inspection.getUser().getNickname(),
                inspection.getDrain().getId(),
                drainName(inspection.getDrain(), adoption),
                photoUrl(inspection.getBeforePhotoKey()),
                photoUrl(inspection.getAfterPhotoKey()),
                inspection.getBeforeGrade(),
                inspection.getAfterGrade(),
                null,
                confidence(inspection.getAiConfidence()),
                inspection.getDistanceM(),
                false,
                inspectionReason(inspection));
    }

    private ReviewItemResponse managementLog(ManagementLog log) {
        Adoption adoption = log.getAdoption();
        return new ReviewItemResponse(
                ReviewTargetType.MANAGEMENT_LOG,
                log.getId(),
                log.getCreatedAt(),
                adoption.getUser().getId(),
                adoption.getUser().getNickname(),
                adoption.getDrain().getId(),
                drainName(adoption.getDrain(), adoption),
                photoUrl(log.getBeforePhotoUrl()),
                photoUrl(log.getAfterPhotoUrl()),
                null,
                null,
                log.getDirtLevel(),
                confidence(log.getAiConfidence()),
                null,
                log.isLocationMismatch(),
                managementLogReason(log));
    }

    private ReviewItemResponse cleaning(Cleaning cleaning) {
        return new ReviewItemResponse(
                ReviewTargetType.CLEANING,
                cleaning.getId(),
                cleaning.getCreatedAt(),
                cleaning.getUser().getId(),
                cleaning.getUser().getNickname(),
                cleaning.getDrain().getId(),
                drainName(cleaning.getDrain(), null),
                photoUrl(cleaning.getBeforePhotoUrl()),
                photoUrl(cleaning.getAfterPhotoUrl()),
                cleaning.getDirtLevel() == null ? null : cleaning.getDirtLevel() - 1,
                cleaning.getAfterGrade(),
                cleaning.getDirtLevel(),
                confidence(cleaning.getDirtConfidence()),
                null,
                cleaning.isLocationMismatch(),
                cleaningReason(cleaning));
    }

    private String inspectionReason(Inspection inspection) {
        List<String> reasons = new ArrayList<>();
        if (inspection.getDistanceM() > policyProperties.locationMismatchMeters()) {
            reasons.add("촬영 위치가 빗물받이에서 " + inspection.getDistanceM() + "m 떨어져 있어요.");
        }
        if (lowConfidence(inspection.getAiConfidence())) {
            reasons.add("점검 전 사진 판정 확신도가 낮아요.");
        }
        if (lowConfidence(inspection.getAfterConfidence())) {
            reasons.add("청소 후 사진 판정 확신도가 낮아요.");
        }
        return joinReason(reasons);
    }

    private String managementLogReason(ManagementLog log) {
        List<String> reasons = new ArrayList<>();
        if (log.isLocationMismatch()) {
            reasons.add("촬영 위치가 빗물받이에서 기준 거리 이상 떨어져 있어요.");
        }
        if (lowConfidence(log.getAiConfidence())) {
            reasons.add("사진 판정 확신도가 낮아요.");
        }
        return joinReason(reasons);
    }

    private String cleaningReason(Cleaning cleaning) {
        List<String> reasons = new ArrayList<>();
        if (cleaning.isLocationMismatch()) {
            reasons.add("촬영 위치가 빗물받이에서 기준 거리 이상 떨어져 있어요.");
        }
        if (lowConfidence(cleaning.getDirtConfidence())) {
            reasons.add("청소 전 사진 판정 확신도가 낮아요.");
        }
        return joinReason(reasons);
    }

    private String joinReason(List<String> reasons) {
        return reasons.isEmpty() ? "관리자 검토가 필요해요." : String.join(" ", reasons);
    }

    private boolean lowConfidence(BigDecimal confidence) {
        return confidence != null
                && confidence.compareTo(policyProperties.aiConfidenceThreshold()) < 0;
    }

    /** AI 확신도는 소수 둘째 자리까지만 내려줍니다. */
    private Double confidence(BigDecimal confidence) {
        return confidence == null ? null : confidence.doubleValue();
    }

    private String photoUrl(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return storageService.toReadUrl(key);
    }

    /** 입양 별명 → 등록 이름 → 빗물받이 #id 순으로 표시 이름을 정합니다. */
    private String drainName(Drain drain, Adoption adoption) {
        if (adoption != null && hasText(adoption.getNickname())) {
            return adoption.getNickname();
        }
        if (hasText(drain.getName())) {
            return drain.getName();
        }
        return "빗물받이 #" + drain.getId();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
