package com.rainbutler.domain.adoption.service;

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
import com.rainbutler.global.util.GeoDistance;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 입양한 빗물받이의 관리(점검 · 청소) 기록을 저장합니다.
 *
 * <ul>
 *   <li>점검 전 · 후 사진을 검증하고 AI로 더러움 단계를 판정합니다.</li>
 *   <li>촬영 위치가 빗물받이에서 기준 거리(기본 50m) 이상 떨어졌거나 AI 확신도가 낮으면 관리자 검토
 *       대기({@code reviewStatus=PENDING})로 남깁니다.</li>
 *   <li>인증에 성공하면 입양의 마지막 관리일 · 다음 관리 예정일을 갱신하고 빗물받이의 최신 사진 ·
 *       더러움 단계를 반영합니다.</li>
 *   <li>비 예보 점검({@code type=RAIN})은 연결된 요청을 완료 처리합니다.</li>
 * </ul>
 *
 * <p>인정된 기록에는 {@link ManagementRewardService}가 개인 포인트와 빗물받이 성장 점수를 지급합니다.
 * (같은 빗물받이는 하루 한 번)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ManagementLogService {

    private final AdoptionRepository adoptionRepository;
    private final ManagementLogRepository managementLogRepository;
    private final InspectionRequestRepository inspectionRequestRepository;
    private final InspectionRequestService inspectionRequestService;
    private final StorageService storageService;
    private final DrainAiClassifier drainAiClassifier;
    private final ManagementCyclePolicy managementCyclePolicy;
    private final ManagementRewardService managementRewardService;
    private final PolicyProperties policyProperties;
    private final UserRepository userRepository;
    private final Clock clock;

    /**
     * 관리 기록을 저장합니다.
     *
     * @param userId 로그인 사용자 id
     * @param adoptionId 입양 id
     * @param request 관리 기록 요청
     * @return 저장한 관리 기록 · 인증 결과 · 다음 관리 예정일
     * @throws BusinessException 입양이 없거나(404) 본인 입양이 아니거나(403) 입양 중이 아니거나 입력이
     *         잘못된 경우(400), 비 예보 요청이 없거나(404) 완료할 수 없거나(409) 이미 기록한 경우(409),
     *         사진이 중복된 경우(409)
     */
    @Transactional
    public ManagementLogResponse record(Long userId, Long adoptionId, ManagementLogRequest request) {
        Adoption adoption = adoptionRepository.findById(adoptionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADOPTION_NOT_FOUND));

        if (!adoption.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (adoption.getStatus() != AdoptionStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "입양 중인 빗물받이만 관리 기록을 남길 수 있어요.");
        }
        if (!Boolean.TRUE.equals(request.safetyAgreed())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "안전 동의가 필요합니다.");
        }

        InspectionRequest inspectionRequest = resolveRequest(adoption, request);
        validateAction(request);

        String beforePhotoKey = storageService.verifyUploaded(
                userId, request.beforePhotoKey(), UploadPurpose.BEFORE);
        String afterPhotoKey = request.actionType() == ManagementActionType.CLEANING
                ? storageService.verifyUploaded(userId, request.afterPhotoKey(), UploadPurpose.AFTER)
                : null;

        rejectDuplicatePhoto(beforePhotoKey);
        if (afterPhotoKey != null) {
            if (afterPhotoKey.equals(beforePhotoKey)) {
                throw new BusinessException(ErrorCode.DUPLICATE_PHOTO);
            }
            rejectDuplicatePhoto(afterPhotoKey);
        }

        DrainAiClassifier.Assessment before =
                drainAiClassifier.assess(storageService.toReadUrl(beforePhotoKey));
        DrainAiClassifier.Assessment after = afterPhotoKey == null
                ? null
                : drainAiClassifier.assess(storageService.toReadUrl(afterPhotoKey));

        Drain drain = adoption.getDrain();
        double distance = GeoDistance.meters(
                request.latitude(), request.longitude(),
                drain.getLatitude(), drain.getLongitude());
        boolean locationMismatch = distance > policyProperties.locationMismatchMeters();
        boolean lowConfidence = isLowConfidence(before, after);
        boolean verified = isVerified(request.actionType(), before, after);

        ManagementLog log = ManagementLog.builder()
                .adoption(adoption)
                .request(inspectionRequest)
                .type(request.type())
                .actionType(request.actionType())
                .build();
        log.recordPhotos(
                beforePhotoKey,
                afterPhotoKey,
                before.dirtLevel(),
                after == null ? null : after.dirtLevel(),
                BigDecimal.valueOf(before.confidence()),
                request.memo(),
                request.latitude(),
                request.longitude(),
                locationMismatch);
        if (locationMismatch || lowConfidence) {
            log.requireReview();
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (verified) {
            log.markVerified(now);
            adoption.recordManagement(
                    now,
                    managementCyclePolicy.nextManagementAt(adoption.getAdoptedAt(), now));
            drain.updateLatestPhoto(
                    afterPhotoKey == null ? beforePhotoKey : afterPhotoKey,
                    after == null ? before.dirtLevel() : after.dirtLevel());
        } else {
            log.markFailed();
        }

        // 비 예보 요청은 인증 성공 여부와 관계없이 제출로 완료한다. (인증 실패는 관리 주기 갱신에서만 제외)
        if (inspectionRequest != null) {
            inspectionRequestService.complete(inspectionRequest.getId(), userId, now);
            adoption.resetIgnoreCount();
        }

        ManagementLog saved = managementLogRepository.save(log);
        if (verified) {
            managementRewardService.reward(saved, now);
        }

        return new ManagementLogResponse(
                saved.getId(),
                adoptionId,
                saved.getType(),
                saved.getActionType(),
                saved.getStatus(),
                saved.getReviewStatus(),
                saved.isLocationMismatch(),
                before.dirtLevel(),
                after == null ? null : after.dirtLevel(),
                saved.getManagedAt(),
                adoption.getNextManagementAt());
    }

    /**
     * 관리자 검토에서 관리 기록을 승인합니다.
     *
     * <p>AI 판정이 실패였더라도 관리자가 사진을 확인해 인정하면 관리 주기 · 빗물받이 최신 사진을 반영합니다.
     * 이미 인증에 성공한 기록은 관리 주기를 다시 늘리지 않고 검토 결과만 남깁니다.
     *
     * @param logId 관리 기록 id
     * @param adminId 검토한 관리자 id
     * @return 승인된 관리 기록
     * @throws BusinessException 기록이 없거나(REVIEW_NOT_FOUND), 검토 대기가 아니면
     *     (REVIEW_ALREADY_PROCESSED), 관리자가 없으면(USER_NOT_FOUND)
     */
    @Transactional
    public ManagementLog approveReview(Long logId, Long adminId) {
        ManagementLog log = findPendingReview(logId);
        boolean newlyVerified = log.getStatus() != ManagementStatus.VERIFIED;
        LocalDateTime now = LocalDateTime.now(clock);

        log.approve(findAdmin(adminId), now);
        if (newlyVerified) {
            Adoption adoption = log.getAdoption();
            adoption.recordManagement(
                    now,
                    managementCyclePolicy.nextManagementAt(adoption.getAdoptedAt(), now));
            adoption.getDrain().updateLatestPhoto(
                    latestPhotoKey(log),
                    latestDirtLevel(log));
        }
        ManagementLog saved = managementLogRepository.save(log);
        if (newlyVerified) {
            managementRewardService.reward(saved, now);
        }
        return saved;
    }

    /**
     * 관리자 검토에서 관리 기록을 반려합니다. (점검 수 · 봉사 시간 집계에서 빠진다)
     *
     * <p>AI 인증에 성공해 이미 관리 주기가 갱신된 기록은 주기를 되돌리지 않습니다. 집계에서만 뺍니다.
     *
     * @param logId 관리 기록 id
     * @param adminId 검토한 관리자 id
     * @return 반려된 관리 기록
     * @throws BusinessException 기록이 없거나(REVIEW_NOT_FOUND), 검토 대기가 아니면
     *     (REVIEW_ALREADY_PROCESSED), 관리자가 없으면(USER_NOT_FOUND)
     */
    @Transactional
    public ManagementLog rejectReview(Long logId, Long adminId) {
        ManagementLog log = findPendingReview(logId);
        log.reject(findAdmin(adminId), LocalDateTime.now(clock));
        return managementLogRepository.save(log);
    }

    private ManagementLog findPendingReview(Long logId) {
        ManagementLog log = managementLogRepository.findById(logId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
        if (log.getReviewStatus() != ReviewStatus.PENDING) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_PROCESSED);
        }
        return log;
    }

    private User findAdmin(Long adminId) {
        return userRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private String latestPhotoKey(ManagementLog log) {
        return log.getAfterPhotoUrl() == null ? log.getBeforePhotoUrl() : log.getAfterPhotoUrl();
    }

    private Integer latestDirtLevel(ManagementLog log) {
        return log.getAfterDirtLevel() == null ? log.getDirtLevel() : log.getAfterDirtLevel();
    }

    /**
     * 관리 종류에 맞는 비 예보 점검 요청을 찾습니다.
     *
     * <p>정기 점검은 요청을 연결하지 않고, 비 예보 점검은 이 입양에 걸린 요청이어야 합니다.
     */
    private InspectionRequest resolveRequest(Adoption adoption, ManagementLogRequest request) {
        if (request.type() == ManagementType.REGULAR) {
            if (request.requestId() != null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "정기 점검에는 점검 요청 id를 보낼 수 없어요.");
            }
            return null;
        }

        if (request.requestId() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "비 예보 점검은 점검 요청 id가 필요해요.");
        }

        InspectionRequest inspectionRequest = inspectionRequestRepository
                .findById(request.requestId())
                .orElseThrow(() -> new BusinessException(ErrorCode.REQUEST_NOT_FOUND));

        if (inspectionRequest.getAdoption() == null
                || !inspectionRequest.getAdoption().getId().equals(adoption.getId())) {
            throw new BusinessException(ErrorCode.REQUEST_NOT_COMPLETABLE);
        }
        if (managementLogRepository.existsByRequest_Id(request.requestId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }
        return inspectionRequest;
    }

    /**
     * 관리 행동에 맞는 사진이 왔는지 확인합니다.
     *
     * <p>청소까지 했으면 청소 후 사진이 필요하고, 살펴보기만 했으면 청소 후 사진을 받지 않습니다.
     */
    private void validateAction(ManagementLogRequest request) {
        boolean hasAfterPhoto = request.afterPhotoKey() != null
                && !request.afterPhotoKey().isBlank();

        if (request.actionType() == ManagementActionType.CLEANING && !hasAfterPhoto) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "청소까지 했다면 청소 후 사진을 등록해 주세요.");
        }
        if (request.actionType() == ManagementActionType.INSPECTION && hasAfterPhoto) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "살펴보기만 했다면 청소 후 사진을 보낼 수 없어요.");
        }
    }

    /**
     * 같은 사진을 다른 관리 기록에서 재사용하지 못하게 합니다.
     */
    private void rejectDuplicatePhoto(String photoKey) {
        if (managementLogRepository.existsByBeforePhotoUrl(photoKey)
                || managementLogRepository.existsByAfterPhotoUrl(photoKey)) {
            throw new BusinessException(ErrorCode.DUPLICATE_PHOTO);
        }
    }

    /**
     * AI 확신도가 기준보다 낮은지 확인합니다. (전 · 후 사진 중 하나라도 낮으면 검토 대기)
     */
    private boolean isLowConfidence(
            DrainAiClassifier.Assessment before, DrainAiClassifier.Assessment after) {
        double threshold = policyProperties.aiConfidenceThreshold().doubleValue();
        return before.confidence() < threshold
                || (after != null && after.confidence() < threshold);
    }

    /**
     * 관리 인증 통과 여부를 판정합니다.
     *
     * <p>살펴보기만 한 점검은 사진이 정상이면 통과하고, 청소까지 했다면 청소 후 등급이 점검 전보다
     * 낮아야 통과합니다. (입양 전 청소 인증과 같은 기준)
     */
    private boolean isVerified(
            ManagementActionType actionType,
            DrainAiClassifier.Assessment before,
            DrainAiClassifier.Assessment after) {
        if (actionType == ManagementActionType.INSPECTION) {
            return true;
        }
        return after != null && after.grade() < before.grade();
    }
}
