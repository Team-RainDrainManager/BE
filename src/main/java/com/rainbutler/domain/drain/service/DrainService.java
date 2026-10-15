package com.rainbutler.domain.drain.service;

import com.rainbutler.domain.adoption.entity.Adoption;
import com.rainbutler.domain.adoption.entity.AdoptionStatus;
import com.rainbutler.domain.adoption.repository.AdoptionRepository;
import com.rainbutler.domain.drain.dto.Request.DrainAssessmentRequest;
import com.rainbutler.domain.drain.dto.Request.DrainCreateRequest;
import com.rainbutler.domain.drain.dto.Response.DrainAssessmentResponse;
import com.rainbutler.domain.drain.dto.Response.DrainCreateResponse;
import com.rainbutler.domain.drain.dto.Response.DrainDetailResponse;
import com.rainbutler.domain.drain.dto.Response.DrainResponse;
import com.rainbutler.domain.drain.entity.Drain;
import com.rainbutler.domain.drain.entity.DrainStatus;
import com.rainbutler.domain.drain.repository.DrainRepository;
import com.rainbutler.domain.report.entity.Report;
import com.rainbutler.domain.report.entity.ReportType;
import com.rainbutler.domain.report.repository.ReportRepository;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.global.config.PolicyProperties;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.storage.StorageService;
import com.rainbutler.global.storage.UploadPurpose;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DrainService {

    private final DrainRepository drainRepository;
    private final AdoptionRepository adoptionRepository;
    private final UserRepository userRepository;
    private final ReportRepository reportRepository;
    private final DrainAiClassifier drainAiClassifier;
    private final PolicyProperties policyProperties;
    private final StorageService storageService;

    /**
     * 지도 영역 내 빗물받이 목록 조회
     */
    public List<DrainResponse> getDrainList(
            Double minLatitude,
            Double maxLatitude,
            Double minLongitude,
            Double maxLongitude
    ) {
        // 1. 위도 범위 검증: -90 ~ 90
        if (minLatitude < -90 || minLatitude > 90
                || maxLatitude < -90 || maxLatitude > 90) {
            throw new BusinessException(
                    ErrorCode.LOCATION_INVALID_INPUT,
                    "위도는 -90 이상 90 이하여야 합니다."
            );
        }
        // 2. 경도 범위 검증: -180 ~ 180
        if (minLongitude < -180 || minLongitude > 180
                || maxLongitude < -180 || maxLongitude > 180) {
            throw new BusinessException(
                    ErrorCode.LOCATION_INVALID_INPUT,
                    "경도는 -180 이상 180 이하여야 합니다."
            );
        }

        // 3. 최소값이 최댓값보다 큰 경우 검증
        if (minLatitude > maxLatitude) {
            throw new BusinessException(
                    ErrorCode.LOCATION_INVALID_INPUT,
                    "최소 위도는 최대 위도보다 클 수 없습니다."
            );
        }
        if (minLongitude > maxLongitude) {
            throw new BusinessException(
                    ErrorCode.LOCATION_INVALID_INPUT,
                    "최소 경도는 최대 경도보다 클 수 없습니다."
            );
        }
        List<Drain> drains =
                drainRepository.findByLatitudeBetweenAndLongitudeBetween(
                        minLatitude,
                        maxLatitude,
                        minLongitude,
                        maxLongitude
                );

        return drains.stream()
                .map(drain -> DrainResponse.builder()
                        .id(drain.getId())
                        .latitude(drain.getLatitude())
                        .longitude(drain.getLongitude())
                        .adoptionStatus(drain.getAdoptionStatus())
                        .isRisk(drain.isRisk())
                        .build())
                .toList();
    }

    public DrainDetailResponse getDrainDetail(Long drainId) {
        Drain drain = drainRepository.findById(drainId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.DRAIN_NOT_FOUND)
                );
        Adoption adoption = adoptionRepository
                .findByDrain_IdAndStatus(
                        drainId,
                        AdoptionStatus.ACTIVE
                )
                .orElse(null);

        DrainDetailResponse.AdoptionResponse adoptionResponse = null;
        if (adoption != null) {
            adoptionResponse = new DrainDetailResponse.AdoptionResponse(
                    adoption.getId(),
                    adoption.getNickname(),
                    adoption.getLevel(),
                    adoption.getScore(),
                    adoption.getAdoptedAt(),
                    adoption.getLastManagedAt(),
                    adoption.getNextManagementAt()
            );
        }

        return new DrainDetailResponse(
                drain.getId(),
                drain.getStatus().name(),
                drain.getAdoptionStatus().name(),
                drain.getLatitude(),
                drain.getLongitude(),
                storageService.toReadUrl(drain.getPhotoUrl()),
                drain.getDirtLevel(),
                adoptionResponse
        );

    }

    /**
     * 사진을 단독 판독합니다. (DB에 저장하지 않습니다)
     *
     * <p>생성·정기 점검·청소 확인이 같은 판정값을 씁니다. 빗물받이가 아닌 사진은 거절합니다.
     */
    public DrainAssessmentResponse assessPhoto(DrainAssessmentRequest request) {
        if (request.photoUrl() == null || request.photoUrl().isBlank()) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "판독할 사진을 등록해 주세요."
            );
        }
        DrainAiClassifier.Assessment assessment = drainAiClassifier.assess(request.photoUrl());
        return new DrainAssessmentResponse(
                assessment.grade(),
                assessment.dirtLevel(),
                assessment.risk(),
                assessment.needsReport(),
                assessment.confidence(),
                assessment.reason()
        );
    }

    /**
     * 빗물받이를 생성합니다. (관리자 직접 등록 + 일반 사용자 제보 등록 공통)
     *
     * <p>상태는 항상 ACTIVE(바로 지도 노출)로 만듭니다. 더러움 단계와 위험 여부는 서버 AI가
     * 사진으로 판정하고 그 값으로 저장합니다. 3등급(위험)이면 지자체 신고용 제보(BLOCKAGE, PENDING)를
     * 만듭니다. 아래 경우 리마인드({@code riskRemind=true})도 함께 띄웁니다.
     * <ul>
     *   <li>사용자가 체크한 위험값이 AI 판정과 다름</li>
     *   <li>AI 확신도가 기준({@code policy.ai-confidence-threshold}) 미만</li>
     * </ul>
     */
    @Transactional
    public DrainCreateResponse createDrain(Long userId, DrainCreateRequest request) {
        // 1. 위도 범위 검증: -90 ~ 90 (Bean Validation과 같은 기준, 에러 코드는 LOCATION_INVALID_INPUT)
        if (request.latitude() < -90 || request.latitude() > 90) {
            throw new BusinessException(
                    ErrorCode.LOCATION_INVALID_INPUT,
                    "위도는 -90 이상 90 이하여야 합니다."
            );
        }
        // 2. 경도 범위 검증: -180 ~ 180
        if (request.longitude() < -180 || request.longitude() > 180) {
            throw new BusinessException(
                    ErrorCode.LOCATION_INVALID_INPUT,
                    "경도는 -180 이상 180 이하여야 합니다."
            );
        }
        // 3. 사진: 본인이 S3에 올린 사진인지 검증 (key 형식 · 업로드 여부 · 타입 · 크기). DB에는 key를 저장한다
        String photoKey = storageService.verifyUploaded(
                userId, request.photoKey(), UploadPurpose.DRAIN);

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.USER_NOT_FOUND)
                );

        // 4. 같은 사진으로 등록된 빗물받이가 있으면 거절
        if (drainRepository.existsByPhotoUrl(photoKey)) {
            throw new BusinessException(ErrorCode.DUPLICATE_PHOTO);
        }

        // 5. 사진 1장으로 AI 판정 (더러움 단계 + 위험 여부). AI에는 조회용 presigned URL을 넘긴다
        DrainAiClassifier.Assessment assessment =
                drainAiClassifier.assess(storageService.toReadUrl(photoKey));
        int dirtLevel = assessment.dirtLevel();
        // 올린 사람이 위험하다고 하면 AI 결과와 관계없이 위험으로 저장
        boolean isRisk = Boolean.TRUE.equals(request.isRisk()) || assessment.risk();

        // 6. 리마인드(불일치·저확신) 또는 위험(3등급)이면 제보 생성
        boolean mismatched = request.isRisk() != null && request.isRisk() != isRisk;
        boolean lowConfidence =
                assessment.confidence() < policyProperties.aiConfidenceThreshold().doubleValue();
        boolean riskRemind = mismatched || lowConfidence;
        boolean needsReport = riskRemind || isRisk;

        Drain drain = Drain.builder()
                .registeredBy(user)
                .latitude(request.latitude())
                .longitude(request.longitude())
                .photoUrl(photoKey)
                .dirtLevel(dirtLevel)
                .status(DrainStatus.ACTIVE)
                .isRisk(isRisk)
                .build();
        Drain saved = drainRepository.save(drain);

        Long riskReportId = null;
        if (needsReport) {
            String description = request.riskDescription();
            if (description == null || description.isBlank()) {
                if (isRisk) {
                    description = "AI 판정 위험(3등급)으로 지자체 신고 대상";
                } else if (mismatched) {
                    description = "사용자 체크(위험=" + request.isRisk() + ")와 AI 판정(위험="
                            + isRisk + ")이 달라 관리자 확인 필요";
                } else {
                    description = "AI 확신도(" + assessment.confidence() + ")가 기준 미만이라 관리자 확인 필요";
                }
            }
            Report report = Report.builder()
                    .user(user)
                    .drain(saved)
                    .type(ReportType.BLOCKAGE)
                    .description(description)
                    .photoUrl(photoKey)
                    .build();
            riskReportId = reportRepository.save(report).getId();
        }

        return new DrainCreateResponse(
                saved.getId(),
                saved.getStatus().name(),
                saved.getAdoptionStatus().name(),
                saved.getLatitude(),
                saved.getLongitude(),
                storageService.toReadUrl(saved.getPhotoUrl()),
                saved.getDirtLevel(),
                saved.isRisk(),
                assessment.confidence(),
                riskRemind,
                riskReportId
        );
    }
}