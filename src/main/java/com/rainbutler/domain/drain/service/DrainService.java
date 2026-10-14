package com.rainbutler.domain.drain.service;

import com.rainbutler.domain.adoption.entity.Adoption;
import com.rainbutler.domain.adoption.entity.AdoptionStatus;
import com.rainbutler.domain.adoption.repository.AdoptionRepository;
import com.rainbutler.domain.drain.dto.Request.DrainCreateRequest;
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
                drain.getPhotoUrl(),
                drain.getDirtLevel(),
                adoptionResponse
        );

    }

    /**
     * 빗물받이를 생성합니다. (관리자 직접 등록 + 일반 사용자 제보 등록 공통)
     *
     * <p>상태는 항상 ACTIVE(바로 지도 노출)로 만듭니다. 더러움 단계와 위험 여부는 서버 AI가
     * 사진으로 판정하고 그 값으로 저장합니다. 아래 경우 리마인드({@code riskRemind=true})와 함께
     * 관리자 검토용 제보(BLOCKAGE, PENDING)를 만듭니다.
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
        // 3. 사진 필수 (빈 문자열 방지)
        if (request.photoUrl() == null || request.photoUrl().isBlank()) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "현장 사진을 등록해 주세요."
            );
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.USER_NOT_FOUND)
                );

        // 4. 사진 1장으로 AI 판정 (더러움 단계 + 위험 여부) → 그 값으로 생성
        DrainAiClassifier.Assessment assessment = drainAiClassifier.assess(request.photoUrl());
        int dirtLevel = assessment.dirtLevel();
        boolean isRisk = assessment.risk();

        // 5. 사용자 체크 불일치 또는 AI 저확신이면 리마인드 + 관리자 검토용 제보
        boolean mismatched = request.isRisk() != null && request.isRisk() != isRisk;
        boolean lowConfidence =
                assessment.confidence() < policyProperties.aiConfidenceThreshold().doubleValue();
        boolean needsReview = mismatched || lowConfidence;

        Drain drain = Drain.builder()
                .registeredBy(user)
                .latitude(request.latitude())
                .longitude(request.longitude())
                .photoUrl(request.photoUrl())
                .dirtLevel(dirtLevel)
                .status(DrainStatus.ACTIVE)
                .isRisk(isRisk)
                .build();
        Drain saved = drainRepository.save(drain);

        Long riskReportId = null;
        if (needsReview) {
            String description = request.riskDescription();
            if (description == null || description.isBlank()) {
                if (mismatched) {
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
                    .photoUrl(request.photoUrl())
                    .build();
            riskReportId = reportRepository.save(report).getId();
        }

        return new DrainCreateResponse(
                saved.getId(),
                saved.getStatus().name(),
                saved.getAdoptionStatus().name(),
                saved.getLatitude(),
                saved.getLongitude(),
                saved.getPhotoUrl(),
                saved.getDirtLevel(),
                saved.isRisk(),
                assessment.confidence(),
                needsReview,
                riskReportId
        );
    }
}