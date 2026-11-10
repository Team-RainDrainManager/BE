package com.rainbutler.domain.inspection.service;

import com.rainbutler.domain.adoption.entity.Adoption;
import com.rainbutler.domain.adoption.entity.AdoptionStatus;
import com.rainbutler.domain.adoption.repository.AdoptionRepository;
import com.rainbutler.domain.drain.entity.DrainDisplayStatus;
import com.rainbutler.domain.drain.service.DrainDisplayStatusPolicy;
import com.rainbutler.domain.inspection.dto.InspectionDetailResponse;
import com.rainbutler.domain.inspection.dto.InspectionListResponse;
import com.rainbutler.domain.inspection.dto.InspectionSummaryResponse;
import com.rainbutler.domain.inspection.entity.Inspection;
import com.rainbutler.domain.inspection.entity.InspectionStatus;
import com.rainbutler.domain.inspection.repository.InspectionRepository;
import com.rainbutler.domain.report.entity.ReportStatus;
import com.rainbutler.domain.report.entity.ReportType;
import com.rainbutler.domain.report.repository.ReportRepository;
import com.rainbutler.domain.user.entity.Role;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.global.config.PolicyProperties;
import com.rainbutler.global.entity.ReviewStatus;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.storage.StorageService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 점검 기록 상세 · 입양별 이력을 조회합니다.
 *
 * <p>점검한 본인과 해당 입양의 입양자, 관리자(ADMIN · CITY_OFFICIAL)만 볼 수 있습니다. 사진은 저장 key를
 * 조회 URL로 바꾸고, 촬영일로부터 사진 보존 기간이 지나면 URL 대신 {@code photoDeleted=true}를 내려줍니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InspectionQueryService {

    /** 한 페이지 기본 개수 */
    public static final int DEFAULT_SIZE = 20;
    /** 한 페이지 최대 개수 (넘으면 이 값으로 줄인다) */
    public static final int MAX_SIZE = 50;

    private final InspectionRepository inspectionRepository;
    private final AdoptionRepository adoptionRepository;
    private final UserRepository userRepository;
    private final ReportRepository reportRepository;
    private final StorageService storageService;
    private final PolicyProperties policyProperties;
    private final DrainDisplayStatusPolicy drainDisplayStatusPolicy;
    private final Clock clock;

    /**
     * 점검 기록 한 건을 상세 조회합니다.
     *
     * @param requesterId 로그인 사용자 id
     * @param inspectionId 점검 기록 id
     * @return 점검 기록 상세
     * @throws BusinessException 사용자 없음 (USER_NOT_FOUND), 기록 없음 (INSPECTION_NOT_FOUND),
     *     볼 권한 없음 (FORBIDDEN)
     */
    public InspectionDetailResponse findDetail(Long requesterId, Long inspectionId) {
        User requester = findRequester(requesterId);
        Inspection inspection = inspectionRepository.findById(inspectionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INSPECTION_NOT_FOUND));
        requireReadable(requester, inspection);

        boolean photoDeleted = photoExpired(inspection);
        Adoption activeAdoption = adoptionRepository
                .findByDrain_IdAndStatus(inspection.getDrain().getId(), AdoptionStatus.ACTIVE)
                .orElse(null);
        int level = activeAdoption == null ? 0 : activeAdoption.getLevel();
        boolean hasBlockageReport = reportRepository.existsByDrain_IdAndTypeAndStatus(
                        inspection.getDrain().getId(), ReportType.BLOCKAGE, ReportStatus.PENDING)
                || reportRepository.existsByDrain_IdAndBlocksAdoptionTrueAndStatus(
                        inspection.getDrain().getId(), ReportStatus.APPROVED);
        DrainDisplayStatus displayStatus = drainDisplayStatusPolicy.resolve(
                activeAdoption, hasBlockageReport, LocalDateTime.now(clock));

        return new InspectionDetailResponse(
                inspection.getId(),
                new InspectionDetailResponse.DrainResponse(
                        inspection.getDrain().getId(), drainName(inspection)),
                inspection.getKind(),
                inspection.getCreatedAt(),
                review(inspection),
                reviewReason(inspection),
                photoUrl(inspection.getBeforePhotoKey(), photoDeleted),
                photoUrl(inspection.getAfterPhotoKey(), photoDeleted),
                inspection.getBeforeGrade(),
                inspection.getAfterGrade(),
                cleaned(inspection),
                inspection.getDistanceM(),
                inspection.getXpAwarded(),
                inspection.getNote(),
                photoDeleted,
                level,
                displayStatus,
                hasBlockageReport);
    }

    /**
     * 입양별 점검 기록을 최신순으로 한 페이지 조회합니다.
     *
     * <p>size + 1개를 읽어 다음 페이지가 있는지 판단합니다. 다음 페이지가 있으면 이번 페이지 마지막 기록
     * id가 {@code nextCursor}가 됩니다.
     *
     * @param requesterId 로그인 사용자 id
     * @param adoptionId 입양 id
     * @param cursor 이전 페이지의 nextCursor (첫 페이지는 null)
     * @param size 페이지 크기 (null이면 20, 50 초과면 50)
     * @return 점검 기록 목록 한 페이지
     * @throws BusinessException size · cursor가 1보다 작음 (INVALID_INPUT), 입양 없음
     *     (ADOPTION_NOT_FOUND), 볼 권한 없음 (FORBIDDEN)
     */
    public InspectionListResponse findByAdoption(
            Long requesterId, Long adoptionId, Long cursor, Integer size) {
        int pageSize = size == null ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        if (pageSize < 1 || (cursor != null && cursor < 1)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "size와 cursor는 1 이상이어야 해요.");
        }
        User requester = findRequester(requesterId);
        Adoption adoption = adoptionRepository.findById(adoptionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADOPTION_NOT_FOUND));
        if (!isPrivileged(requester) && !adoption.getUser().getId().equals(requesterId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        List<Inspection> rows = inspectionRepository.findPageByAdoptionId(
                adoptionId,
                cursor == null ? Long.MAX_VALUE : cursor,
                PageRequest.of(0, pageSize + 1));
        boolean hasNext = rows.size() > pageSize;
        List<InspectionSummaryResponse> page = rows.stream()
                .limit(pageSize)
                .map(this::summary)
                .toList();
        Long nextCursor = hasNext ? page.get(page.size() - 1).inspectionId() : null;
        return new InspectionListResponse(nextCursor, page);
    }

    private InspectionSummaryResponse summary(Inspection inspection) {
        boolean photoDeleted = photoExpired(inspection);
        return new InspectionSummaryResponse(
                inspection.getId(),
                inspection.getKind(),
                inspection.getCreatedAt(),
                review(inspection),
                photoUrl(inspection.getBeforePhotoKey(), photoDeleted),
                photoUrl(inspection.getAfterPhotoKey(), photoDeleted),
                inspection.getBeforeGrade(),
                inspection.getAfterGrade(),
                cleaned(inspection),
                inspection.getXpAwarded(),
                photoDeleted);
    }

    private User findRequester(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * 점검한 본인 · 해당 입양의 입양자 · 관리자만 기록을 볼 수 있습니다.
     */
    private void requireReadable(User requester, Inspection inspection) {
        if (isPrivileged(requester)) {
            return;
        }
        if (inspection.getUser().getId().equals(requester.getId())) {
            return;
        }
        if (inspection.getAdoption() != null
                && inspection.getAdoption().getUser().getId().equals(requester.getId())) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    private boolean isPrivileged(User user) {
        return user.getRole() == Role.ADMIN || user.getRole() == Role.CITY_OFFICIAL;
    }

    /**
     * 점검 상태를 검토 상태로 보여줍니다.
     *
     * <p>완료된 기록도 위치 · 확신도 기준을 다시 계산해, AI만으로 통과한 기록은
     * {@code NOT_REQUIRED}, 검토를 거쳐 승인된 기록은 {@code APPROVED}로 구분합니다.
     */
    private ReviewStatus review(Inspection inspection) {
        return switch (inspection.getStatus()) {
            case PENDING -> ReviewStatus.PENDING;
            case REJECTED -> ReviewStatus.REJECTED;
            case COMPLETED -> reviewRequired(inspection)
                    ? ReviewStatus.APPROVED
                    : ReviewStatus.NOT_REQUIRED;
            case NEEDS_CLEANING -> ReviewStatus.NOT_REQUIRED;
        };
    }

    /**
     * 검토가 필요한 이유를 사람이 읽을 수 있는 문구로 만듭니다.
     *
     * @return 검토 대기 · 반려가 아니면 null
     */
    private String reviewReason(Inspection inspection) {
        if (inspection.getStatus() == InspectionStatus.REJECTED) {
            return "관리자 검토에서 반려됐어요.";
        }
        if (inspection.getStatus() != InspectionStatus.PENDING) {
            return null;
        }

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
        return reasons.isEmpty() ? "관리자 검토가 필요해요." : String.join(" ", reasons);
    }

    /**
     * 제출 때와 같은 기준(위치 불일치 · 낮은 확신도)으로 검토 필요 여부를 다시 판단합니다.
     */
    private boolean reviewRequired(Inspection inspection) {
        return inspection.getDistanceM() > policyProperties.locationMismatchMeters()
                || lowConfidence(inspection.getAiConfidence())
                || lowConfidence(inspection.getAfterConfidence());
    }

    private boolean lowConfidence(BigDecimal confidence) {
        return confidence != null
                && confidence.compareTo(policyProperties.aiConfidenceThreshold()) < 0;
    }

    /**
     * 촬영일로부터 사진 보존 기간이 지났는지 확인합니다.
     */
    private boolean photoExpired(Inspection inspection) {
        LocalDateTime expiresAt =
                inspection.getCapturedAt().plusDays(policyProperties.photoRetentionDays());
        return !expiresAt.isAfter(LocalDateTime.now(clock));
    }

    private String photoUrl(String key, boolean photoDeleted) {
        if (photoDeleted || key == null || key.isBlank()) {
            return null;
        }
        return storageService.toReadUrl(key);
    }

    /**
     * 빗물받이에는 별도 이름이 없어 입양 당시 별명을 씁니다. 입양이 없던 기록은 id로 표기합니다.
     */
    private String drainName(Inspection inspection) {
        Adoption adoption = inspection.getAdoption();
        if (adoption != null && adoption.getNickname() != null
                && !adoption.getNickname().isBlank()) {
            return adoption.getNickname();
        }
        return "빗물받이 #" + inspection.getDrain().getId();
    }

    /**
     * 청소 후 사진을 낸 기록인지 확인합니다.
     */
    private boolean cleaned(Inspection inspection) {
        return inspection.getAfterGrade() != null;
    }
}
