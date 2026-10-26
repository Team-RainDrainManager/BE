package com.rainbutler.domain.cleaning.service;

import com.rainbutler.domain.adoption.entity.AdoptionStatus;
import com.rainbutler.domain.adoption.repository.AdoptionRepository;
import com.rainbutler.domain.cleaning.dto.Request.CleaningStartRequest;
import com.rainbutler.domain.cleaning.dto.Response.CleaningStartResponse;
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
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.storage.StorageService;
import com.rainbutler.global.storage.UploadPurpose;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CleaningService {

    private static final int MAX_ADOPTION_COUNT = 3;
    private static final double LOCATION_MISMATCH_THRESHOLD_METERS = 50.0;

    private final CleaningRepository cleaningRepository;
    private final DrainRepository drainRepository;
    private final AdoptionRepository adoptionRepository;
    private final UserRepository userRepository;
    private final ReportRepository reportRepository;
    private final DrainAiClassifier drainAiClassifier;
    private final StorageService storageService;

    /**
     * 입양 청소를 시작합니다.
     *
     * <p>AI 판정 결과에 따라:
     * <ul>
     *   <li>0등급 → 바로 입양 (현재는 청소만 생성, 입양은 별도 API)</li>
     *   <li>1~2등급 → 청소 진행</li>
     *   <li>3등급·needsReport=true → 입양 불가, BLOCKAGE 제보로 전환</li>
     * </ul>
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

        // 3. 사용자 입양 수 제한 확인
        long adoptionCount = adoptionRepository.countByUser_IdAndStatus(userId, AdoptionStatus.ACTIVE);
        if (adoptionCount >= MAX_ADOPTION_COUNT) {
            throw new BusinessException(ErrorCode.ADOPTION_LIMIT_EXCEEDED);
        }

        // 4. 사진 검증
        String photoKey = storageService.verifyUploaded(userId, request.photoKey(), UploadPurpose.BEFORE);

        // 5. 사진 중복 확인
        if (cleaningRepository.existsByBeforePhotoUrl(photoKey)) {
            throw new BusinessException(ErrorCode.DUPLICATE_PHOTO);
        }

        // 6. AI 판정
        DrainAiClassifier.Assessment assessment =
                drainAiClassifier.assess(storageService.toReadUrl(photoKey));

        // 7. 촬영 위치와 빗물받이 위치 거리 계산
        double distance = calculateDistance(
                request.latitude(), request.longitude(),
                drain.getLatitude(), drain.getLongitude());
        boolean locationMismatch = distance > LOCATION_MISMATCH_THRESHOLD_METERS;

        // 8. 3등급·needsReport=true면 입양 불가, BLOCKAGE 제보로 전환
        boolean blocked = assessment.dirtLevel() == 3 && assessment.needsReport();
        if (blocked) {
            // BLOCKAGE 제보 생성
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            Report report = Report.builder()
                    .user(user)
                    .drain(drain)
                    .type(ReportType.BLOCKAGE)
                    .description("AI 판정: 덮개 안쪽 막힘(3등급)으로 지자체 신고 대상")
                    .photoUrl(photoKey)
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

        // 9. 청소 생성 (0등급, 1~2등급 모두 청소 생성)
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
        cleaning.setDirtConfidence(java.math.BigDecimal.valueOf(assessment.confidence()));
        cleaning.setStatus(CleaningStatus.IN_PROGRESS);
        cleaning.setReviewStatus(com.rainbutler.global.entity.ReviewStatus.PENDING);
        cleaning.setLocationMismatch(locationMismatch);

        Cleaning saved = cleaningRepository.save(cleaning);

        // 10. 응답
        boolean mustClean = assessment.dirtLevel() >= 1 && assessment.dirtLevel() <= 2;
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
     * 두 좌표 사이의 거리를 미터 단위로 계산합니다. (Haversine 공식)
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000; // 지구 반지름 (미터)

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }
}
