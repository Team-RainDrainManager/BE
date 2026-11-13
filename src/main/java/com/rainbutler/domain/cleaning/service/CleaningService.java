package com.rainbutler.domain.cleaning.service;

import com.rainbutler.domain.adoption.entity.AdoptionStatus;
import com.rainbutler.domain.adoption.repository.AdoptionRepository;
import com.rainbutler.domain.cleaning.dto.Request.CleaningStartRequest;
import com.rainbutler.domain.cleaning.dto.Request.CleaningVerifyRequest;
import com.rainbutler.domain.cleaning.dto.Response.CleaningStartResponse;
import com.rainbutler.domain.cleaning.dto.Response.CleaningVerifyResponse;
import com.rainbutler.domain.cleaning.entity.Cleaning;
import com.rainbutler.domain.cleaning.entity.CleaningStatus;
import com.rainbutler.domain.cleaning.entity.CleaningType;
import com.rainbutler.domain.cleaning.repository.CleaningRepository;
import com.rainbutler.domain.drain.entity.Drain;
import com.rainbutler.domain.drain.entity.DrainAdoptionStatus;
import com.rainbutler.domain.drain.repository.DrainRepository;
import com.rainbutler.domain.drain.service.DrainAiClassifier;
import com.rainbutler.domain.report.entity.Report;
import com.rainbutler.domain.report.entity.ReportStatus;
import com.rainbutler.domain.report.entity.ReportType;
import com.rainbutler.domain.report.repository.ReportRepository;
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
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CleaningService {

    private static final int MAX_ADOPTION_COUNT = 3;

    /** 입양을 막는 제보 상태 (반려 · 해제되면 풀린다) */
    private static final List<ReportStatus> BLOCKING_STATUSES =
            List.of(ReportStatus.PENDING, ReportStatus.APPROVED);

    private final CleaningRepository cleaningRepository;
    private final DrainRepository drainRepository;
    private final AdoptionRepository adoptionRepository;
    private final UserRepository userRepository;
    private final ReportRepository reportRepository;
    private final DrainAiClassifier drainAiClassifier;
    private final StorageService storageService;
    private final PolicyProperties policyProperties;

    /**
     * 입양 청소를 시작합니다.
     *
     * <p>촬영 위치가 빗물받이에서 정책 거리({@code location-mismatch-meters})를 넘으면 청소를 시작하지
     * 않고 다시 촬영하게 합니다.
     *
     * <p>AI 판정 결과에 따라:
     * <ul>
     *   <li>괜찮음(1) → 청소를 완료 상태로 만들고 바로 입양 가능 ({@code mustClean=false})</li>
     *   <li>쌓임(2) · 막힘(3) → 청소 진행 ({@code mustClean=true}), 청소 후 사진 인증이 필요하다</li>
     *   <li>needsReport=true 또는 막힘(3) → 입양 불가, 입양을 막는 BLOCKAGE 제보로 전환</li>
     * </ul>
     *
     * @throws BusinessException 빗물받이가 없거나, 이미 입양됐거나, 입양 한도를 넘었거나, 촬영 위치가
     *     빗물받이와 너무 멀면
     */
    @Transactional
    public CleaningStartResponse startAdoptionCleaning(Long userId, Long drainId, CleaningStartRequest request) {
        // 1. 빗물받이 존재 확인
        Drain drain = drainRepository.findById(drainId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DRAIN_NOT_FOUND));

        // 2. 빗물받이 입양 상태 확인
        if (drain.getAdoptionStatus() == DrainAdoptionStatus.ADOPTED) {
            throw new BusinessException(ErrorCode.DRAIN_ALREADY_ADOPTED);
        }

        // 2-1. AI가 청소 불가로 판정한 제보가 남아 있으면 입양 청소를 시작할 수 없다
        if (reportRepository.existsByDrain_IdAndBlocksAdoptionTrueAndStatusIn(
                drainId, BLOCKING_STATUSES)) {
            throw new BusinessException(ErrorCode.DRAIN_ADOPTION_BLOCKED);
        }

        // 3. 사용자 입양 수 제한 확인
        long adoptionCount = adoptionRepository.countByUser_IdAndStatus(userId, AdoptionStatus.ACTIVE);
        if (adoptionCount >= MAX_ADOPTION_COUNT) {
            throw new BusinessException(ErrorCode.ADOPTION_LIMIT_EXCEEDED);
        }

        // 4. 촬영 위치가 빗물받이 근처인지 확인 (멀면 AI 판정 전에 다시 촬영하게 한다)
        requireNearDrain(request.latitude(), request.longitude(), drain);

        // 5. 사진 검증
        String photoKey = storageService.verifyUploaded(userId, request.photoKey(), UploadPurpose.BEFORE);

        // 6. 사진 중복 확인
        if (cleaningRepository.existsByBeforePhotoUrl(photoKey)) {
            throw new BusinessException(ErrorCode.DUPLICATE_PHOTO);
        }

        // 7. AI 판정
        DrainAiClassifier.Assessment assessment =
                drainAiClassifier.assess(storageService.toReadUrl(photoKey));

        // 8. 막힘(3등급) 또는 needsReport=true면 입양 불가, BLOCKAGE 제보로 전환
        boolean blocked = assessment.needsReport()
                || assessment.grade() == DrainAiClassifier.GRADE_BLOCKED;
        if (blocked) {
            // BLOCKAGE 제보 생성 (관리자가 해제할 때까지 입양 차단)
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            Report report = Report.builder()
                    .user(user)
                    .drain(drain)
                    .type(ReportType.BLOCKAGE)
                    .description("AI 판정: 청소가 어려운 상태(덮개 안쪽 막힘 또는 막힘(3등급))로 지자체 신고 대상")
                    .photoUrl(photoKey)
                    .latitude(request.latitude())
                    .longitude(request.longitude())
                    .blocksAdoption(true)
                    .build();
            reportRepository.save(report);

            // 청소는 생성하지 않음
            return new CleaningStartResponse(
                    null,
                    assessment.grade(),
                    assessment.dirtLevel(),
                    assessment.needsReport(),
                    false,
                    true
            );
        }

        // 9. 청소 생성 (1등급 괜찮음은 청소 없이 입양할 수 있도록 완료 상태로 만든다)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Cleaning cleaning = Cleaning.builder()
                .user(user)
                .drain(drain)
                .type(CleaningType.ADOPTION)
                .beforePhotoUrl(photoKey)
                .latitude(request.latitude())
                .longitude(request.longitude())
                .startedAt(LocalDateTime.now())
                .build();

        // builder에 없는 필드 설정
        cleaning.setDirtLevel(assessment.dirtLevel());
        cleaning.setDirtConfidence(BigDecimal.valueOf(assessment.confidence()));
        cleaning.setStatus(CleaningStatus.IN_PROGRESS);

        // 이미 깨끗한 빗물받이(괜찮음 1)는 청소 인증 없이 바로 입양할 수 있게 한다
        boolean alreadyClean = DrainAiClassifier.isClean(assessment.grade());
        if (alreadyClean) {
            cleaning.setStatus(CleaningStatus.COMPLETED);
            cleaning.setCompletedAt(LocalDateTime.now());
            drain.updateLatestPhoto(photoKey, assessment.dirtLevel());
        }

        Cleaning saved = cleaningRepository.save(cleaning);

        // 10. 응답 (괜찮음(1)은 청소 불필요, 쌓임(2)·막힘(3)은 청소 후 사진 인증이 필요하다)
        boolean mustClean = !alreadyClean;
        return new CleaningStartResponse(
                saved.getId(),
                assessment.grade(),
                assessment.dirtLevel(),
                assessment.needsReport(),
                mustClean,
                false
        );
    }

    /**
     * 촬영 위치가 빗물받이에서 정책 거리 안인지 확인합니다.
     *
     * @param latitude 촬영 위도
     * @param longitude 촬영 경도
     * @param drain 촬영한 빗물받이
     * @throws BusinessException 정책 거리({@code location-mismatch-meters})를 넘으면
     */
    private void requireNearDrain(double latitude, double longitude, Drain drain) {
        double distance = GeoDistance.meters(
                latitude, longitude, drain.getLatitude(), drain.getLongitude());
        if (distance > policyProperties.locationMismatchMeters()) {
            throw new BusinessException(ErrorCode.CLEANING_LOCATION_MISMATCH);
        }
    }

    /**
     * 청소 완료 인증을 진행합니다.
     *
     * <p>청소 후 사진 위치가 빗물받이에서 멀거나, 점검 전 사진을 찍은 위치와 크게 차이 나면 인증하지 않고
     * 다시 촬영하게 합니다.
     *
     * <p>AI 판정 결과에 따라:
     * <ul>
     *   <li>afterGrade &lt; beforeGrade: 인증 통과 (passed=true) → 빗물받이 최신 사진 · 더러움 단계를
     *       청소 후 사진 기준으로 갱신</li>
     *   <li>afterGrade &gt;= beforeGrade: 인증 실패 (passed=false), 재시도 가능</li>
     * </ul>
     * 인증 통과해야 입양 가능합니다.
     */
    @Transactional
    public CleaningVerifyResponse verifyCleaning(Long userId, Long cleaningId, CleaningVerifyRequest request) {
        // 1. 청소 존재 확인
        Cleaning cleaning = cleaningRepository.findById(cleaningId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        // 2. 본인의 청소인지 확인
        if (!cleaning.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 3. 청소 상태 확인 (진행 중인 청소만 인증 가능)
        if (cleaning.getStatus() != CleaningStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 완료된 청소입니다.");
        }

        // 4. 청소 후 촬영 위치 확인 (빗물받이 근처 + 점검 전 사진과 같은 자리)
        requireNearDrain(request.latitude(), request.longitude(), cleaning.getDrain());
        double distanceFromBefore = GeoDistance.meters(
                request.latitude(), request.longitude(),
                cleaning.getLatitude(), cleaning.getLongitude());
        if (distanceFromBefore > policyProperties.locationMismatchMeters()) {
            throw new BusinessException(ErrorCode.CLEANING_LOCATION_MISMATCH);
        }

        // 5. 사진 검증
        String photoKey = storageService.verifyUploaded(userId, request.photoKey(), UploadPurpose.AFTER);

        // 6. AI 판정 (청소 후 사진)
        DrainAiClassifier.Assessment assessment =
                drainAiClassifier.assess(storageService.toReadUrl(photoKey));

        // 7. beforeGrade 계산 (dirtLevel에서 역변환: dirtLevel 1→괜찮음 1, 2→쌓임 2, 3→막힘 3)
        int beforeGrade = cleaning.getDirtLevel() != null
                ? DrainAiClassifier.gradeOfDirtLevel(cleaning.getDirtLevel())
                : DrainAiClassifier.GRADE_OK;
        int afterGrade = assessment.grade();

        // 8. 인증 시도 횟수 증가
        cleaning.incrementAttemptNo();

        // 9. 청소 후 정보 저장
        cleaning.setAfterPhotoUrl(photoKey);
        cleaning.setAfterGrade(afterGrade);

        // 10. 인증 통과 여부 판정 (afterGrade < beforeGrade)
        boolean passed = afterGrade < beforeGrade;

        // 11. 통과하면 청소 완료 상태로 변경하고, 빗물받이의 최신 사진을 청소 후 사진으로 갱신
        if (passed) {
            cleaning.setStatus(CleaningStatus.COMPLETED);
            cleaning.setCompletedAt(LocalDateTime.now());
            cleaning.getDrain().updateLatestPhoto(photoKey, assessment.dirtLevel());

            // 위치가 멀거나 AI 확신도가 낮으면 봉사 시간 · 점검 수에 넣기 전에 관리자가 확인한다
            if (cleaning.isLocationMismatch() || lowConfidence(assessment.confidence())) {
                cleaning.requireReview();
            } else {
                cleaning.clearReview();
            }
        }

        Cleaning saved = cleaningRepository.save(cleaning);

        return new CleaningVerifyResponse(
                saved.getId(),
                beforeGrade,
                afterGrade,
                passed,
                saved.getAttemptNo()
        );
    }

    /**
     * 관리자 검토에서 청소를 승인합니다.
     *
     * @param cleaningId 청소 id
     * @param adminId 검토한 관리자 id
     * @return 승인된 청소
     * @throws BusinessException 청소가 없거나(REVIEW_NOT_FOUND), 검토 대기가 아니면
     *         (REVIEW_ALREADY_PROCESSED), 관리자가 없으면(USER_NOT_FOUND)
     */
    @Transactional
    public Cleaning approveReview(Long cleaningId, Long adminId) {
        Cleaning cleaning = findPendingReview(cleaningId);
        cleaning.approve(findAdmin(adminId), LocalDateTime.now());
        return cleaningRepository.save(cleaning);
    }

    /**
     * 관리자 검토에서 청소를 반려합니다. (봉사 시간 · 점검 수에서 빠진다)
     *
     * @param cleaningId 청소 id
     * @param adminId 검토한 관리자 id
     * @return 반려된 청소
     * @throws BusinessException 청소가 없거나(REVIEW_NOT_FOUND), 검토 대기가 아니면
     *         (REVIEW_ALREADY_PROCESSED), 관리자가 없으면(USER_NOT_FOUND)
     */
    @Transactional
    public Cleaning rejectReview(Long cleaningId, Long adminId) {
        Cleaning cleaning = findPendingReview(cleaningId);
        cleaning.reject(findAdmin(adminId), LocalDateTime.now());
        return cleaningRepository.save(cleaning);
    }

    private Cleaning findPendingReview(Long cleaningId) {
        Cleaning cleaning = cleaningRepository.findById(cleaningId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
        if (cleaning.getStatus() != CleaningStatus.COMPLETED
                || cleaning.getReviewStatus() != ReviewStatus.PENDING) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_PROCESSED);
        }
        return cleaning;
    }

    private User findAdmin(Long adminId) {
        return userRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    /** AI 확신도가 정책 기준보다 낮은지 확인합니다. */
    private boolean lowConfidence(double confidence) {
        return BigDecimal.valueOf(confidence)
                .compareTo(policyProperties.aiConfidenceThreshold()) < 0;
    }
}
