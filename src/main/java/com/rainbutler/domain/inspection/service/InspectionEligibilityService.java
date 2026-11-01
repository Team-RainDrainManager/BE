package com.rainbutler.domain.inspection.service;

import com.rainbutler.domain.adoption.entity.Adoption;
import com.rainbutler.domain.adoption.entity.AdoptionStatus;
import com.rainbutler.domain.adoption.repository.AdoptionRepository;
import com.rainbutler.domain.drain.repository.DrainRepository;
import com.rainbutler.domain.inspection.dto.InspectionEligibilityReason;
import com.rainbutler.domain.inspection.dto.InspectionEligibilityResponse;
import com.rainbutler.domain.request.entity.InspectionRequest;
import com.rainbutler.domain.request.entity.RequestStatus;
import com.rainbutler.domain.request.repository.InspectionRequestRepository;
import com.rainbutler.domain.sanction.service.SanctionService;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 정기·비 예보·대신 점검을 시작할 수 있는지 판정합니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InspectionEligibilityService {

    private final DrainRepository drainRepository;
    private final AdoptionRepository adoptionRepository;
    private final InspectionRequestRepository inspectionRequestRepository;
    private final SanctionService sanctionService;
    private final Clock clock;

    /**
     * 점검 화면 진입 가능 여부를 판정합니다.
     *
     * <p>요청 id가 없으면 본인의 ACTIVE 입양인 정기 점검만 허용합니다. 요청 id가 있으면 비 예보 요청의
     * 입양자가 가능 응답한 {@code ACCEPTED} 상태 또는 본인이 맡은 {@code CLAIMED} 상태만 허용합니다.
     * 실제 점검 제출 시에는 같은 조건을 다시 검증해야 합니다.
     *
     * @param userId 로그인 사용자 id
     * @param drainId 점검할 빗물받이 id
     * @param requestId 비 예보·대신 점검 요청 id (정기 점검이면 null)
     * @return 점검 가능 여부와 불가 사유
     * @throws BusinessException 빗물받이 또는 요청이 없을 때
     */
    public InspectionEligibilityResponse check(Long userId, Long drainId, Long requestId) {
        if (!drainRepository.existsById(drainId)) {
            throw new BusinessException(ErrorCode.DRAIN_NOT_FOUND);
        }
        if (sanctionService.isSanctioned(userId)) {
            return ineligible(InspectionEligibilityReason.SANCTIONED,
                    "제재 기간에는 점검을 진행할 수 없어요.");
        }
        if (requestId == null) {
            return checkRoutine(userId, drainId);
        }
        return checkRequest(userId, drainId, requestId, LocalDateTime.now(clock));
    }

    private InspectionEligibilityResponse checkRoutine(Long userId, Long drainId) {
        boolean owner = adoptionRepository.findByDrain_IdAndStatus(drainId, AdoptionStatus.ACTIVE)
                .map(Adoption::getUser)
                .map(user -> user.getId().equals(userId))
                .orElse(false);
        return owner
                ? InspectionEligibilityResponse.allowed()
                : ineligible(InspectionEligibilityReason.NOT_ASSIGNED,
                        "입양한 빗물받이만 정기 점검할 수 있어요.");
    }

    private InspectionEligibilityResponse checkRequest(
            Long userId, Long drainId, Long requestId, LocalDateTime now) {
        InspectionRequest request = inspectionRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REQUEST_NOT_FOUND));
        if (!request.getDrain().getId().equals(drainId)) {
            return ineligible(InspectionEligibilityReason.NOT_ASSIGNED,
                    "이 빗물받이에 연결된 점검 요청이 아니에요.");
        }
        if (!now.isBefore(request.getCompleteDeadlineAt()) || !request.isInProgress()) {
            return ineligible(InspectionEligibilityReason.REQUEST_CLOSED,
                    "점검 요청이 종료됐거나 완료 마감 시간이 지났어요.");
        }

        RequestStatus status = request.getStatus();
        boolean owner = request.getAdoption() != null
                && request.getAdoption().getUser().getId().equals(userId);
        if (owner && status == RequestStatus.SENT) {
            return ineligible(InspectionEligibilityReason.REPLY_REQUIRED,
                    "비 예보 점검 요청에 가능 여부를 먼저 응답해 주세요.");
        }
        if (owner && status == RequestStatus.ACCEPTED) {
            return InspectionEligibilityResponse.allowed();
        }
        boolean claimant = request.getSubstituteUser() != null
                && request.getSubstituteUser().getId().equals(userId);
        if (claimant && status == RequestStatus.CLAIMED) {
            return InspectionEligibilityResponse.allowed();
        }
        return ineligible(InspectionEligibilityReason.NOT_ASSIGNED,
                "이 점검 요청을 수행하도록 배정되지 않았어요.");
    }

    private InspectionEligibilityResponse ineligible(
            InspectionEligibilityReason reason, String message) {
        return InspectionEligibilityResponse.ineligible(reason, message);
    }
}
