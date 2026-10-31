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
import com.rainbutler.domain.drain.dto.Response.GrowthResponse;
import com.rainbutler.domain.drain.entity.Drain;
import com.rainbutler.domain.drain.repository.DrainRepository;
import com.rainbutler.global.config.PolicyProperties;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.storage.StorageService;
import com.rainbutler.global.storage.UploadPurpose;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DrainService {

    /** 판독할 수 있는 사진 용도 (등록 · 청소 · 점검 전후) */
    private static final Set<UploadPurpose> ASSESSABLE_PURPOSES =
            Set.of(UploadPurpose.DRAIN, UploadPurpose.BEFORE, UploadPurpose.AFTER);

    private final DrainRepository drainRepository;
    private final AdoptionRepository adoptionRepository;
    private final DrainAiClassifier drainAiClassifier;
    private final DrainWriter drainWriter;
    private final PolicyProperties policyProperties;
    private final StorageService storageService;
    private final GrowthLevelPolicy growthLevelPolicy;

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
     * 사진은 본인이 S3에 올린 DRAIN · BEFORE · AFTER 용도 사진만 받습니다.
     */
    public DrainAssessmentResponse assessPhoto(Long userId, DrainAssessmentRequest request) {
        // 본인이 올린 사진인지 검증 (key 형식 · 용도 · 업로드 여부 · 타입 · 크기)
        String photoKey =
                storageService.verifyUploaded(userId, request.photoKey(), ASSESSABLE_PURPOSES);
        // AI에는 우리 버킷의 조회용 presigned URL만 넘긴다 (임의 주소를 내려받지 않음)
        DrainAiClassifier.Assessment assessment =
                drainAiClassifier.assess(storageService.toReadUrl(photoKey));
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
     * <p>트랜잭션을 열지 않고 검증·AI 판정을 끝낸 뒤, 저장은 짧은 트랜잭션({@code DrainWriter})에
     * 맡깁니다. 상태는 항상 ACTIVE(바로 지도 노출)로 만듭니다. 3등급(위험)이면 지자체 신고용
     * 제보(BLOCKAGE, PENDING)를 만듭니다. 아래 경우 리마인드({@code riskRemind=true})도 함께 띄웁니다.
     * <ul>
     *   <li>사용자가 체크한 위험값이 AI 판정과 다름</li>
     *   <li>AI 확신도가 기준({@code policy.ai-confidence-threshold}) 미만</li>
     * </ul>
     */
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

        // 4. 같은 사진으로 등록된 빗물받이가 있으면 거절 (AI 호출 전 빠른 실패)
        if (drainRepository.existsByPhotoUrl(photoKey)) {
            throw new BusinessException(ErrorCode.DUPLICATE_PHOTO);
        }

        // 5. 사진 1장으로 AI 판정 (더러움 단계 + 위험 여부) — 트랜잭션 밖 외부 호출
        DrainAiClassifier.Assessment assessment = drainAiClassifier.assess(storageService.toReadUrl(photoKey));
        int dirtLevel = assessment.dirtLevel();
        // 올린 사람이 위험하다고 하면 AI 결과와 관계없이 위험으로 저장
        boolean isRisk = Boolean.TRUE.equals(request.isRisk()) || assessment.risk();

        // 6. 리마인드(불일치·저확신) 또는 위험(3등급)이면 제보 사유 준비
        boolean mismatched = request.isRisk() != null && request.isRisk() != isRisk;
        boolean lowConfidence =
                assessment.confidence() < policyProperties.aiConfidenceThreshold().doubleValue();
        boolean riskRemind = mismatched || lowConfidence;

        String reportDescription = null;
        if (riskRemind || isRisk) {
            reportDescription = request.riskDescription();
            if (reportDescription == null || reportDescription.isBlank()) {
                if (isRisk) {
                    reportDescription = "AI 판정 위험(3등급)으로 지자체 신고 대상";
                } else if (mismatched) {
                    reportDescription = "사용자 체크(위험=" + request.isRisk() + ")와 AI 판정(위험="
                            + isRisk + ")이 달라 관리자 확인 필요";
                } else {
                    reportDescription = "AI 확신도(" + assessment.confidence() + ")가 기준 미만이라 관리자 확인 필요";
                }
            }
        }

        // 7. 저장만 짧은 트랜잭션으로
        DrainWriter.SavedDrain saved = drainWriter.save(
                userId, request, photoKey, dirtLevel, isRisk, reportDescription);

        return new DrainCreateResponse(
                saved.drain().getId(),
                saved.drain().getStatus().name(),
                saved.drain().getAdoptionStatus().name(),
                saved.drain().getLatitude(),
                saved.drain().getLongitude(),
                storageService.toReadUrl(saved.drain().getPhotoUrl()),
                saved.drain().getDirtLevel(),
                saved.drain().isRisk(),
                assessment.confidence(),
                riskRemind,
                saved.riskReportId()
        );
    }

    /**
     * 빗물받이 성장 정보를 조회합니다.
     *
     * <p>성장은 입양에 귀속되므로 ACTIVE 입양이 없으면 조회할 수 없습니다. 집사가 바뀌면 새 입양이
     * 1레벨 · 0점으로 시작해 성장 기록도 초기화됩니다.
     *
     * @param drainId 빗물받이 id
     * @return 현재 레벨 · 점수 · 다음 레벨 점수 · 진행률
     */
    public GrowthResponse getGrowth(Long drainId) {
        if (!drainRepository.existsById(drainId)) {
            throw new BusinessException(ErrorCode.DRAIN_NOT_FOUND);
        }

        Adoption adoption = adoptionRepository
                .findByDrain_IdAndStatus(drainId, AdoptionStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADOPTION_NOT_FOUND));

        int level = adoption.getLevel();
        int score = adoption.getScore();

        return new GrowthResponse(
                level,
                score,
                growthLevelPolicy.nextLevelScore(level, score),
                growthLevelPolicy.progressPercent(level, score)
        );
    }
}
