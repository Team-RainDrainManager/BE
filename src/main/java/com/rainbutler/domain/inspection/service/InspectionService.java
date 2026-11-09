package com.rainbutler.domain.inspection.service;

import com.rainbutler.domain.adoption.entity.Adoption;
import com.rainbutler.domain.adoption.entity.AdoptionStatus;
import com.rainbutler.domain.adoption.repository.AdoptionRepository;
import com.rainbutler.domain.adoption.service.ManagementCyclePolicy;
import com.rainbutler.domain.drain.entity.Drain;
import com.rainbutler.domain.drain.repository.DrainRepository;
import com.rainbutler.domain.drain.service.DrainAiClassifier;
import com.rainbutler.domain.inspection.dto.InspectionCompleteRequest;
import com.rainbutler.domain.inspection.dto.InspectionEligibilityResponse;
import com.rainbutler.domain.inspection.dto.InspectionStartRequest;
import com.rainbutler.domain.inspection.dto.InspectionSubmitResponse;
import com.rainbutler.domain.inspection.entity.Inspection;
import com.rainbutler.domain.inspection.entity.InspectionKind;
import com.rainbutler.domain.inspection.entity.InspectionStatus;
import com.rainbutler.domain.inspection.repository.InspectionRepository;
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
import com.rainbutler.global.util.GeoDistance;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 점검 · 청소 기록을 제출합니다.
 *
 * <p>점검 전 사진으로 기록을 만들고, 빗물받이가 더러우면 청소 후 사진을 같은 기록에 붙입니다. AI가 점검 전 ·
 * 후 사진을 판정하고, 위치가 너무 멀거나 확신도가 낮으면 관리자 검토 대기로 남깁니다.
 *
 * <p>인증을 통과하면 관리 주기 · 빗물받이 최신 사진을 갱신하고 경험치 · 성장 점수를 지급합니다. 비 예보 ·
 * 대신 점검은 연결된 요청을 완료 처리하고 입양의 연속 무응답 횟수를 초기화합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InspectionService {

    private static final String ROUTINE_DESCRIPTION = "정기 점검";
    private static final String FORECAST_DESCRIPTION = "비 예보 점검";
    private static final String SUBSTITUTE_DESCRIPTION = "대신 점검";

    private final InspectionRepository inspectionRepository;
    private final DrainRepository drainRepository;
    private final AdoptionRepository adoptionRepository;
    private final InspectionRequestRepository inspectionRequestRepository;
    private final InspectionEligibilityService inspectionEligibilityService;
    private final InspectionRequestService inspectionRequestService;
    private final InspectionRewardService inspectionRewardService;
    private final StorageService storageService;
    private final DrainAiClassifier drainAiClassifier;
    private final ManagementCyclePolicy managementCyclePolicy;
    private final PolicyProperties policyProperties;
    private final UserRepository userRepository;
    private final Clock clock;

    /**
     * 점검 전 사진으로 점검을 시작합니다.
     *
     * <p>AI 판정이 깨끗하면 바로 점검을 마치고, 더러우면 {@code NEEDS_CLEANING}으로 남겨 청소 후 사진을
     * 받습니다. 위치 불일치 · 낮은 확신도면 {@code PENDING}으로 관리자 검토를 기다립니다.
     *
     * @param userId 점검하는 사용자 id
     * @param request 점검 시작 요청
     * @return 점검 기록 id · 상태 · AI 판정 · 지급 경험치
     * @throws BusinessException 안전 확인 누락 · 권한 없음 · 제재 중 · 요청 없음/종료 · 사진 중복 등
     */
    @Transactional
    public InspectionSubmitResponse start(Long userId, InspectionStartRequest request) {
        requireSafetyConfirmed(request.safetyConfirmed());

        Drain drain = drainRepository.findById(request.drainId())
                .orElseThrow(() -> new BusinessException(ErrorCode.DRAIN_NOT_FOUND));
        requireEligible(userId, drain.getId(), request.requestId());
        InspectionContext context =
                resolveContext(userId, drain, request.kind(), request.requestId());

        String beforeKey = storageService.verifyUploaded(
                userId, request.beforeKey(), UploadPurpose.BEFORE);
        rejectDuplicatePhoto(beforeKey);

        DrainAiClassifier.Assessment before =
                drainAiClassifier.assess(storageService.toReadUrl(beforeKey));
        int distanceM = distanceMeters(
                request.latitude(), request.longitude(), drain);
        boolean reviewRequired = reviewRequired(before.confidence(), null, distanceM);

        Inspection inspection = Inspection.builder()
                .user(context.user())
                .drain(drain)
                .adoption(context.adoption())
                .request(context.request())
                .kind(request.kind())
                .beforePhotoKey(beforeKey)
                .beforeGrade(before.grade())
                .aiConfidence(BigDecimal.valueOf(before.confidence()))
                .distanceM(distanceM)
                .capturedAt(request.capturedAt())
                .note(request.note())
                .build();

        if (reviewRequired) {
            inspection.requireReview();
        } else if (before.grade() == 0) {
            inspection.completeWithoutCleaning();
        }

        Inspection saved = inspectionRepository.save(inspection);
        LocalDateTime now = LocalDateTime.now(clock);
        applyAfterSubmit(saved, context.growthAdoption(), now, false);

        return response(saved, notice(saved.getStatus(), false));
    }

    /**
     * 청소 후 사진으로 점검을 마무리합니다.
     *
     * <p>청소 후 등급이 점검 전보다 좋아져야 인증을 통과합니다. 좋아지지 않으면 다시 청소할 수 있게
     * {@code NEEDS_CLEANING}으로 남깁니다.
     *
     * @param userId 점검한 사용자 id
     * @param inspectionId 점검 기록 id
     * @param request 청소 후 사진 인증 요청
     * @return 점검 기록 id · 상태 · AI 판정 · 지급 경험치
     * @throws BusinessException 안전 확인 누락 · 점검 기록 없음 · 본인 점검 아님 · 이미 완료/검토 중 · 사진 중복 등
     */
    @Transactional
    public InspectionSubmitResponse complete(
            Long userId, Long inspectionId, InspectionCompleteRequest request) {
        requireSafetyConfirmed(request.safetyConfirmed());

        Inspection inspection = inspectionRepository.findById(inspectionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INSPECTION_NOT_FOUND));
        if (!inspection.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (inspection.getStatus() != InspectionStatus.NEEDS_CLEANING) {
            throw new BusinessException(ErrorCode.INSPECTION_ALREADY_COMPLETED);
        }
        requireRequestOpen(inspection);

        String afterKey = storageService.verifyUploaded(
                userId, request.afterKey(), UploadPurpose.AFTER);
        if (afterKey.equals(inspection.getBeforePhotoKey())) {
            throw new BusinessException(ErrorCode.DUPLICATE_PHOTO);
        }
        rejectDuplicatePhoto(afterKey);

        DrainAiClassifier.Assessment after =
                drainAiClassifier.assess(storageService.toReadUrl(afterKey));
        boolean improved = after.grade() < inspection.getBeforeGrade();
        boolean reviewRequired = reviewRequired(
                inspection.getAiConfidence().doubleValue(), after.confidence(),
                inspection.getDistanceM());

        if (!improved) {
            inspection.retryCleaning(afterKey, after.grade(), after.confidence());
        } else if (reviewRequired) {
            inspection.requireReviewAfterCleaning(afterKey, after.grade(), after.confidence());
        } else {
            inspection.completeCleaning(afterKey, after.grade(), after.confidence());
        }

        Inspection saved = inspectionRepository.save(inspection);
        LocalDateTime now = LocalDateTime.now(clock);
        applyAfterSubmit(saved, resolveGrowthAdoption(saved), now, true);

        return response(saved, notice(saved.getStatus(), true));
    }

    /**
     * 관리자 검토에서 점검을 승인합니다.
     *
     * <p>승인하면 인증을 통과한 점검과 같은 후처리를 합니다. 관리 주기 · 빗물받이 최신 사진을 갱신하고
     * 경험치를 지급합니다. 검토 대기로 남을 때 이미 완료 처리한 점검 요청은 다시 완료하지 않습니다.
     *
     * @param inspectionId 점검 기록 id
     * @param adminId 검토한 관리자 id
     * @return 승인된 점검 기록
     * @throws BusinessException 기록이 없거나(REVIEW_NOT_FOUND), 검토 대기가 아니면
     *     (REVIEW_ALREADY_PROCESSED), 관리자가 없으면(USER_NOT_FOUND)
     */
    @Transactional
    public Inspection approveReview(Long inspectionId, Long adminId) {
        Inspection inspection = findPendingReview(inspectionId);
        LocalDateTime now = LocalDateTime.now(clock);
        inspection.approve(findAdmin(adminId), now);
        applyCompletion(inspection, resolveGrowthAdoption(inspection), now);
        return inspectionRepository.save(inspection);
    }

    /**
     * 관리자 검토에서 점검을 반려합니다. (점검 수 · 봉사 시간 집계에서 빠진다)
     *
     * @param inspectionId 점검 기록 id
     * @param adminId 검토한 관리자 id
     * @return 반려된 점검 기록
     * @throws BusinessException 기록이 없거나(REVIEW_NOT_FOUND), 검토 대기가 아니면
     *     (REVIEW_ALREADY_PROCESSED), 관리자가 없으면(USER_NOT_FOUND)
     */
    @Transactional
    public Inspection rejectReview(Long inspectionId, Long adminId) {
        Inspection inspection = findPendingReview(inspectionId);
        inspection.reject(findAdmin(adminId), LocalDateTime.now(clock));
        return inspectionRepository.save(inspection);
    }

    private Inspection findPendingReview(Long inspectionId) {
        Inspection inspection = inspectionRepository.findById(inspectionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
        if (inspection.getStatus() != InspectionStatus.PENDING) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_PROCESSED);
        }
        return inspection;
    }

    private User findAdmin(Long adminId) {
        return userRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * 제출 결과에 따라 요청 완료 · 인증 후처리를 실행합니다.
     */
    private void applyAfterSubmit(
            Inspection inspection, Adoption growthAdoption, LocalDateTime now,
            boolean afterSubmitted) {
        if (inspection.getStatus() == InspectionStatus.COMPLETED) {
            applyCompletion(inspection, growthAdoption, now);
        } else if (inspection.getStatus() == InspectionStatus.PENDING) {
            completeRequest(inspection, growthAdoption, now);
        }
    }

    /**
     * 인증을 통과한 점검의 관리 주기 · 빗물받이 사진 · 경험치를 반영합니다.
     */
    private void applyCompletion(Inspection inspection, Adoption growthAdoption, LocalDateTime now) {
        Drain drain = inspection.getDrain();
        String latestPhotoKey = inspection.getAfterPhotoKey() == null
                ? inspection.getBeforePhotoKey()
                : inspection.getAfterPhotoKey();
        Integer latestGrade = inspection.getAfterGrade() == null
                ? inspection.getBeforeGrade()
                : inspection.getAfterGrade();
        drain.updateLatestPhoto(latestPhotoKey, dirtLevel(latestGrade));

        if (growthAdoption != null) {
            growthAdoption.recordManagement(now, managementCyclePolicy.nextManagementAt(now));
        }
        inspectionRewardService.reward(
                inspection, growthAdoption, description(inspection.getKind()), now);
        completeRequest(inspection, growthAdoption, now);
    }

    /**
     * 연결된 비 예보 · 대신 점검 요청을 완료하고 입양의 연속 무응답 횟수를 초기화합니다.
     *
     * <p>검토 대기로 남을 때 이미 요청을 완료하므로, 승인 후처리에서 다시 불러도 안전하도록 완료된 요청은
     * 건너뜁니다.
     */
    private void completeRequest(Inspection inspection, Adoption adoption, LocalDateTime now) {
        InspectionRequest request = inspection.getRequest();
        if (request == null || request.getStatus() == RequestStatus.COMPLETED) {
            return;
        }
        inspectionRequestService.complete(
                request.getId(), inspection.getUser().getId(), now);
        if (adoption != null) {
            adoption.resetIgnoreCount();
        }
    }

    /**
     * 비 예보 · 대신 점검 요청의 완료 마감과 상태를 제출 시 다시 확인합니다.
     */
    private void requireRequestOpen(Inspection inspection) {
        InspectionRequest request = inspection.getRequest();
        if (request == null) {
            return;
        }
        if (!LocalDateTime.now(clock).isBefore(request.getCompleteDeadlineAt())
                || !request.isInProgress()) {
            throw new BusinessException(ErrorCode.REQUEST_NOT_COMPLETABLE,
                    "점검 요청 완료 마감이 지났거나 이미 종료된 요청이에요.");
        }
    }

    /**
     * 점검 화면 진입 API와 같은 기준으로 제출 권한을 다시 확인합니다.
     */
    private void requireEligible(Long userId, Long drainId, Long requestId) {
        InspectionEligibilityResponse eligibility =
                inspectionEligibilityService.check(userId, drainId, requestId);
        if (eligibility.eligible()) {
            return;
        }
        switch (eligibility.reason()) {
            case SANCTIONED -> throw new BusinessException(ErrorCode.USER_SANCTIONED);
            case NOT_ASSIGNED -> throw new BusinessException(ErrorCode.FORBIDDEN);
            case REPLY_REQUIRED, REQUEST_CLOSED -> throw new BusinessException(
                    ErrorCode.REQUEST_NOT_COMPLETABLE, eligibility.message());
            default -> throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    /**
     * 점검 종류에 맞는 입양 · 요청을 찾고, 점검 종류와 요청 역할이 맞는지 확인합니다.
     */
    private InspectionContext resolveContext(
            Long userId, Drain drain, InspectionKind kind, Long requestId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (kind == InspectionKind.ROUTINE) {
            if (requestId != null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "정기 점검에는 점검 요청 id를 보낼 수 없어요.");
            }
            Adoption adoption = adoptionRepository
                    .findByDrain_IdAndStatus(drain.getId(), AdoptionStatus.ACTIVE)
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.FORBIDDEN, "입양한 빗물받이만 정기 점검할 수 있어요."));
            return new InspectionContext(user, adoption, null, adoption);
        }

        if (requestId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "비 예보 · 대신 점검은 점검 요청 id가 필요해요.");
        }
        InspectionRequest request = inspectionRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REQUEST_NOT_FOUND));
        if (!request.getDrain().getId().equals(drain.getId())) {
            throw new BusinessException(ErrorCode.REQUEST_NOT_COMPLETABLE);
        }
        if (inspectionRepository.existsByRequest_Id(requestId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }

        if (kind == InspectionKind.FORECAST) {
            if (request.getAdoption() == null
                    || !request.getAdoption().getUser().getId().equals(userId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            if (request.getStatus() != RequestStatus.ACCEPTED) {
                throw new BusinessException(ErrorCode.REQUEST_NOT_COMPLETABLE);
            }
            Adoption adoption = request.getAdoption();
            Adoption growthAdoption = adoptionRepository
                    .findByDrain_IdAndStatus(drain.getId(), AdoptionStatus.ACTIVE)
                    .orElse(adoption);
            return new InspectionContext(user, adoption, request, growthAdoption);
        }

        if (kind == InspectionKind.SUBSTITUTE) {
            if (request.getSubstituteUser() == null
                    || !request.getSubstituteUser().getId().equals(userId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            if (request.getStatus() != RequestStatus.CLAIMED) {
                throw new BusinessException(ErrorCode.REQUEST_NOT_COMPLETABLE);
            }
            Adoption adoption = adoptionRepository
                    .findByDrain_IdAndStatus(drain.getId(), AdoptionStatus.ACTIVE)
                    .orElse(null);
            return new InspectionContext(user, adoption, request, adoption);
        }

        throw new BusinessException(ErrorCode.INVALID_INPUT);
    }

    /**
     * 청소 후 점검에서 성장 점수를 받을 입양을 다시 찾습니다.
     */
    private Adoption resolveGrowthAdoption(Inspection inspection) {
        if (inspection.getAdoption() != null
                && inspection.getAdoption().getStatus() == AdoptionStatus.ACTIVE) {
            return inspection.getAdoption();
        }
        return adoptionRepository
                .findByDrain_IdAndStatus(inspection.getDrain().getId(), AdoptionStatus.ACTIVE)
                .orElse(null);
    }

    private void requireSafetyConfirmed(Boolean safetyConfirmed) {
        if (!Boolean.TRUE.equals(safetyConfirmed)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "현장 안전 수칙 확인이 필요합니다.");
        }
    }

    private void rejectDuplicatePhoto(String photoKey) {
        if (inspectionRepository.existsByBeforePhotoKey(photoKey)
                || inspectionRepository.existsByAfterPhotoKey(photoKey)) {
            throw new BusinessException(ErrorCode.DUPLICATE_PHOTO);
        }
    }

    private int distanceMeters(double latitude, double longitude, Drain drain) {
        return (int) Math.round(GeoDistance.meters(
                latitude, longitude, drain.getLatitude(), drain.getLongitude()));
    }

    private boolean reviewRequired(double beforeConfidence, Double afterConfidence, int distanceM) {
        double threshold = policyProperties.aiConfidenceThreshold().doubleValue();
        return distanceM > policyProperties.locationMismatchMeters()
                || beforeConfidence < threshold
                || (afterConfidence != null && afterConfidence < threshold);
    }

    /** AI 등급을 빗물받이 더러움 단계(1~3)로 바꿉니다. */
    private int dirtLevel(int grade) {
        return DrainAiClassifier.toDirtLevel(grade);
    }

    private String description(InspectionKind kind) {
        return switch (kind) {
            case ROUTINE -> ROUTINE_DESCRIPTION;
            case FORECAST -> FORECAST_DESCRIPTION;
            case SUBSTITUTE -> SUBSTITUTE_DESCRIPTION;
        };
    }

    private String notice(InspectionStatus status, boolean afterSubmitted) {
        return switch (status) {
            case COMPLETED -> afterSubmitted
                    ? "청소 인증을 통과했어요."
                    : "깨끗해요. 점검을 마쳤어요.";
            case PENDING -> afterSubmitted
                    ? "청소는 확인했어요. 사진 검토가 끝나면 관리 기록에 반영돼요."
                    : "사진을 확인하고 있어요. 검토 결과는 관리 기록에 반영돼요.";
            case NEEDS_CLEANING -> afterSubmitted
                    ? "청소 후에도 등급이 좋아지지 않았어요. 다시 청소하고 사진을 올려 주세요."
                    : "빗물받이에 쓰레기가 쌓여 있어요. 청소 후 사진을 올려 주세요.";
            case REJECTED -> "관리자 검토에서 반려됐어요.";
        };
    }

    private InspectionSubmitResponse response(Inspection inspection, String notice) {
        return new InspectionSubmitResponse(
                inspection.getId(),
                inspection.getStatus(),
                inspection.getBeforeGrade(),
                inspection.getAfterGrade(),
                inspection.getAiConfidence() == null
                        ? null
                        : inspection.getAiConfidence().doubleValue(),
                inspection.getDistanceM(),
                inspection.getXpAwarded(),
                notice);
    }

    /**
     * 점검에 연결할 사용자 · 입양 · 요청입니다.
     *
     * @param user 점검한 사용자
     * @param adoption 점검 대상 입양 (입양되지 않은 빗물받이면 null)
     * @param request 비 예보 · 대신 점검 요청 (정기 점검이면 null)
     * @param growthAdoption 성장 점수를 받을 입양 (없으면 null)
     */
    private record InspectionContext(
            User user,
            Adoption adoption,
            InspectionRequest request,
            Adoption growthAdoption) {
    }
}
