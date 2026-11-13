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
import com.rainbutler.domain.drain.entity.DrainDisplayStatus;
import com.rainbutler.domain.drain.repository.DrainRepository;
import com.rainbutler.domain.drain.repository.DrainSpecifications;
import com.rainbutler.domain.report.entity.ReportStatus;
import com.rainbutler.domain.report.entity.ReportType;
import com.rainbutler.domain.report.repository.ReportRepository;
import com.rainbutler.domain.request.dto.RainRequestStatus;
import com.rainbutler.domain.request.dto.RainRequestSummary;
import com.rainbutler.domain.request.service.RainRequestLookup;
import com.rainbutler.global.config.PolicyProperties;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.response.PageResponse;
import com.rainbutler.global.storage.StorageService;
import com.rainbutler.global.storage.UploadPurpose;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DrainService {

    /** 판독할 수 있는 사진 용도 (등록 · 청소 · 점검 전후) */
    private static final Set<UploadPurpose> ASSESSABLE_PURPOSES =
            Set.of(UploadPurpose.DRAIN, UploadPurpose.BEFORE, UploadPurpose.AFTER);

    /** 지도 한 번에 내려주는 최대 개수 */
    private static final int MAP_MAX_RESULTS = 500;

    /** 목록 페이지 크기 상한 */
    private static final int PAGE_MAX_SIZE = 100;

    private final DrainRepository drainRepository;
    private final AdoptionRepository adoptionRepository;
    private final ReportRepository reportRepository;
    private final DrainAiClassifier drainAiClassifier;
    private final DrainWriter drainWriter;
    private final PolicyProperties policyProperties;
    private final StorageService storageService;
    private final GrowthLevelPolicy growthLevelPolicy;
    private final DrainDisplayStatusPolicy drainDisplayStatusPolicy;
    private final DrainAddressResolver drainAddressResolver;
    private final RainRequestLookup rainRequestLookup;
    private final DrainRegionResolver drainRegionResolver;
    private final DrainWeatherAreaService drainWeatherAreaService;
    private final Clock clock;

    /**
     * 지도 영역 내 빗물받이 목록 조회
     *
     * @param status 화면 표시 상태 필터 (없으면 전체)
     * @param keyword 이름 · 주소 · 입양 별명 검색어 (없으면 전체)
     * @param rainStatus 이번 비 대신 점검 상태 필터 (없으면 전체)
     */
    public List<DrainResponse> getDrainList(
            Double minLatitude,
            Double maxLatitude,
            Double minLongitude,
            Double maxLongitude,
            DrainDisplayStatus status,
            String keyword,
            RainRequestStatus rainStatus
    ) {
        validateBounds(minLatitude, maxLatitude, minLongitude, maxLongitude);

        Specification<Drain> spec = specification(
                minLatitude, maxLatitude, minLongitude, maxLongitude, status, keyword)
                .and(rainStatusSpecification(rainStatus));
        Page<Drain> page = drainRepository.findAll(
                spec,
                PageRequest.of(0, MAP_MAX_RESULTS, Sort.by(Sort.Direction.ASC, "id")));

        return toResponses(page.getContent());
    }

    /**
     * 빗물받이 목록을 페이지 단위로 조회합니다. (지도 목록 패널 · 검색 화면)
     *
     * @param page 페이지 번호 (0부터)
     * @param size 페이지 크기 (1~100)
     * @param status 화면 표시 상태 필터 (없으면 전체)
     * @param keyword 이름 · 주소 · 입양 별명 검색어 (없으면 전체)
     * @param rainStatus 이번 비 대신 점검 상태 필터 (없으면 전체)
     * @return 페이지 응답
     */
    public PageResponse<DrainResponse> getDrainPage(
            int page, int size, DrainDisplayStatus status, String keyword,
            RainRequestStatus rainStatus) {
        if (page < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "페이지 번호는 0 이상이어야 합니다.");
        }
        if (size < 1 || size > PAGE_MAX_SIZE) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "페이지 크기는 1 이상 " + PAGE_MAX_SIZE + " 이하여야 합니다.");
        }

        Specification<Drain> spec = (root, query, cb) -> cb.conjunction();
        Specification<Drain> search = DrainSpecifications.search(keyword);
        if (search != null) {
            spec = spec.and(search);
        }
        Specification<Drain> statusSpec =
                DrainSpecifications.displayStatus(status, LocalDateTime.now(clock));
        if (statusSpec != null) {
            spec = spec.and(statusSpec);
        }
        spec = spec.and(rainStatusSpecification(rainStatus));

        Page<Drain> result = drainRepository.findAll(
                spec, PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id")));

        return PageResponse.of(
                toResponses(result.getContent()),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    /**
     * 빗물받이 상세 조회
     *
     * @param userId 로그인 사용자 id (비로그인이면 null)
     * @param drainId 빗물받이 id
     */
    public DrainDetailResponse getDrainDetail(Long userId, Long drainId) {
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
        boolean hasBlockageReport = hasBlockageReport(drainId);
        DrainDisplayStatus displayStatus = drainDisplayStatusPolicy.resolve(
                adoption, hasBlockageReport, LocalDateTime.now(clock));

        int level = adoption == null ? 0 : adoption.getLevel();
        int xp = adoption == null ? 0 : adoption.getScore();
        int nextLevelScore = adoption == null
                ? 0
                : growthLevelPolicy.nextLevelScore(level, xp);
        int progressPercent = adoption == null
                ? 0
                : growthLevelPolicy.progressPercent(level, xp);
        boolean isMine = userId != null
                && adoption != null
                && adoption.getUser().getId().equals(userId);

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
                displayName(drain, adoption),
                drain.getAddress(),
                drain.getStatus().name(),
                drain.getAdoptionStatus().name(),
                displayStatus,
                displayStatus.getLabel(),
                drain.getLatitude(),
                drain.getLongitude(),
                storageService.toReadUrl(drain.getPhotoUrl()),
                drain.getDirtLevel(),
                isMine,
                level,
                growthLevelPolicy.titleFor(level),
                xp,
                nextLevelScore,
                progressPercent,
                adoptionResponse,
                rainRequestLookup.findForDrains(
                        List.of(drain.getId()), LocalDate.now(clock)).get(drain.getId())
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
     * 맡깁니다. 상태는 항상 ACTIVE(바로 지도 노출)로 만듭니다. AI가 청소 불가(막힘(3등급) 또는
     * {@code needsReport=true})로 판정하면 지자체 신고용 제보(BLOCKAGE, PENDING)를 만들어 입양을
     * 막습니다. 아래 경우 리마인드({@code riskRemind=true})도 함께 띄웁니다.
     * <ul>
     *   <li>사용자가 체크한 위험값이 AI 판정과 다름</li>
     *   <li>AI 확신도가 기준({@code policy.ai-confidence-threshold}) 미만</li>
     * </ul>
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
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

        // 6. 이름 · 주소 (주소가 없으면 좌표로 역지오코딩, 실패해도 등록은 계속)
        String name = trimToNull(request.name());
        String address = trimToNull(request.address());
        if (address == null) {
            address = drainAddressResolver.resolve(request.latitude(), request.longitude());
        }
        // 비 예보 알림 문구용 행정동 이름 (실패하면 null, 백필이 다시 채운다)
        String regionName =
                drainRegionResolver.resolve(request.latitude(), request.longitude());

        // 7. 청소 불가 판정이면 입양을 막는 제보를 만들고, 아니면 불일치·저확신 검토용 제보를 만든다.
        boolean uncleanable = assessment.needsReport()
                || assessment.grade() == DrainAiClassifier.GRADE_BLOCKED;
        boolean mismatched = request.isRisk() != null && request.isRisk() != isRisk;
        boolean lowConfidence =
                assessment.confidence() < policyProperties.aiConfidenceThreshold().doubleValue();
        boolean riskRemind = mismatched || lowConfidence;

        String reportDescription = null;
        boolean blocksAdoption = false;
        if (uncleanable) {
            blocksAdoption = true;
            reportDescription = firstNonBlank(
                    request.riskDescription(),
                    "AI 판정: 청소가 어려운 상태(덮개 안쪽 막힘 또는 막힘(3등급))로 지자체 신고 대상");
        } else if (riskRemind || isRisk) {
            reportDescription = request.riskDescription();
            if (reportDescription == null || reportDescription.isBlank()) {
                if (isRisk) {
                    reportDescription = "AI 판정 위험(막힘(3등급))으로 지자체 신고 대상";
                } else if (mismatched) {
                    reportDescription = "사용자 체크(위험=" + request.isRisk() + ")와 AI 판정(위험="
                            + isRisk + ")이 달라 관리자 확인 필요";
                } else {
                    reportDescription = "AI 확신도(" + assessment.confidence() + ")가 기준 미만이라 관리자 확인 필요";
                }
            }
        }

        // 8. 저장만 짧은 트랜잭션으로
        DrainWriter.SavedDrain saved = drainWriter.save(
                userId, request, photoKey, dirtLevel, isRisk, reportDescription, blocksAdoption,
                name, address);
        drainWeatherAreaService.assignRegionName(saved.drain().getId(), regionName);

        DrainDisplayStatus displayStatus = saved.riskReportId() == null
                ? DrainDisplayStatus.AVAILABLE
                : DrainDisplayStatus.BLOCKAGE_REPORTED;

        return new DrainCreateResponse(
                saved.drain().getId(),
                displayName(saved.drain(), null),
                saved.drain().getAddress(),
                saved.drain().getStatus().name(),
                saved.drain().getAdoptionStatus().name(),
                displayStatus,
                displayStatus.getLabel(),
                saved.drain().getLatitude(),
                saved.drain().getLongitude(),
                storageService.toReadUrl(saved.drain().getPhotoUrl()),
                saved.drain().getDirtLevel(),
                saved.drain().isRisk(),
                assessment.confidence(),
                riskRemind,
                saved.riskReportId(),
                saved.blocksAdoption()
        );
    }

    private String firstNonBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
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

    private Specification<Drain> specification(
            double minLatitude,
            double maxLatitude,
            double minLongitude,
            double maxLongitude,
            DrainDisplayStatus status,
            String keyword) {
        Specification<Drain> spec = DrainSpecifications.inBox(
                minLatitude, maxLatitude, minLongitude, maxLongitude);
        Specification<Drain> search = DrainSpecifications.search(keyword);
        if (search != null) {
            spec = spec.and(search);
        }
        Specification<Drain> statusSpec =
                DrainSpecifications.displayStatus(status, LocalDateTime.now(clock));
        if (statusSpec != null) {
            spec = spec.and(statusSpec);
        }
        return spec;
    }

    /**
     * 이번 비 대신 점검 상태 조건입니다. 상태가 없으면 조건 없음.
     */
    private Specification<Drain> rainStatusSpecification(RainRequestStatus rainStatus) {
        if (rainStatus == null) {
            return (root, query, cb) -> cb.conjunction();
        }
        return DrainSpecifications.rainRequestStatus(
                rainStatus, LocalDate.now(clock).atStartOfDay());
    }

    private List<DrainResponse> toResponses(List<Drain> drains) {
        if (drains.isEmpty()) {
            return List.of();
        }

        List<Long> drainIds = drains.stream().map(Drain::getId).toList();
        Map<Long, Adoption> activeAdoptions = adoptionRepository.findActiveByDrainIds(drainIds)
                .stream()
                .collect(Collectors.toMap(
                        adoption -> adoption.getDrain().getId(),
                        Function.identity(),
                        (first, second) -> first));
        Set<Long> blockageDrainIds =
                new HashSet<>(reportRepository.findBlockageReportedDrainIds(drainIds));
        LocalDateTime now = LocalDateTime.now(clock);
        // 이번 비 대신 점검 상태도 빗물받이 id로 한 번에 조회한다
        Map<Long, RainRequestSummary> rainRequests =
                rainRequestLookup.findForDrains(drainIds, now.toLocalDate());

        return drains.stream()
                .map(drain -> toResponse(
                        drain,
                        activeAdoptions.get(drain.getId()),
                        blockageDrainIds.contains(drain.getId()),
                        rainRequests.get(drain.getId()),
                        now))
                .toList();
    }

    /**
     * 지도 · 목록에 막힘 제보로 표시할 제보가 있는지 확인합니다.
     *
     * <p>처리 대기 중인 막힘 제보는 모두 표시하고, 승인된 제보는 입양을 막는 제보만 해제 전까지
     * 표시합니다.
     */
    private boolean hasBlockageReport(Long drainId) {
        return reportRepository.existsByDrain_IdAndTypeAndStatus(
                        drainId, ReportType.BLOCKAGE, ReportStatus.PENDING)
                || reportRepository.existsByDrain_IdAndBlocksAdoptionTrueAndStatus(
                        drainId, ReportStatus.APPROVED);
    }

    private DrainResponse toResponse(
            Drain drain,
            Adoption adoption,
            boolean hasBlockageReport,
            RainRequestSummary rainRequest,
            LocalDateTime now) {
        int level = adoption == null ? 0 : adoption.getLevel();
        int xp = adoption == null ? 0 : adoption.getScore();
        DrainDisplayStatus displayStatus = drainDisplayStatusPolicy.resolve(
                adoption, hasBlockageReport, now);

        return DrainResponse.builder()
                .id(drain.getId())
                .name(displayName(drain, adoption))
                .address(drain.getAddress())
                .latitude(drain.getLatitude())
                .longitude(drain.getLongitude())
                .adoptionStatus(drain.getAdoptionStatus())
                .status(displayStatus.getKey())
                .displayStatus(displayStatus)
                .displayStatusLabel(displayStatus.getLabel())
                .isRisk(drain.isRisk())
                .dirtLevel(drain.getDirtLevel())
                .level(level)
                .levelTitle(growthLevelPolicy.titleFor(level))
                .xp(xp)
                .rainRequest(rainRequest)
                .build();
    }

    /** 입양 별명 → 등록 이름 → 빗물받이 #id 순으로 표시 이름을 정합니다. */
    private String displayName(Drain drain, Adoption adoption) {
        if (adoption != null && StringUtils.hasText(adoption.getNickname())) {
            return adoption.getNickname();
        }
        if (StringUtils.hasText(drain.getName())) {
            return drain.getName();
        }
        return "빗물받이 #" + drain.getId();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void validateBounds(
            double minLatitude, double maxLatitude, double minLongitude, double maxLongitude) {
        // 1. 위도 범위 검증: -90 ~ 90
        if (minLatitude < -90 || minLatitude > 90 || maxLatitude < -90 || maxLatitude > 90) {
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
    }
}
