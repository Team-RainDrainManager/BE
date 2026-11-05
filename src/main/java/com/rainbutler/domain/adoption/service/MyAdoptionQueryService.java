package com.rainbutler.domain.adoption.service;

import com.rainbutler.domain.adoption.dto.Response.AdoptionDetailResponse;
import com.rainbutler.domain.adoption.dto.Response.MyAdoptionListResponse;
import com.rainbutler.domain.adoption.dto.Response.MyAdoptionResponse;
import com.rainbutler.domain.adoption.entity.Adoption;
import com.rainbutler.domain.adoption.entity.AdoptionStatus;
import com.rainbutler.domain.adoption.repository.AdoptionRepository;
import com.rainbutler.domain.drain.entity.Drain;
import com.rainbutler.domain.drain.entity.DrainDisplayStatus;
import com.rainbutler.domain.drain.service.DrainDisplayStatusPolicy;
import com.rainbutler.domain.drain.service.GrowthLevelPolicy;
import com.rainbutler.domain.report.entity.ReportStatus;
import com.rainbutler.domain.report.entity.ReportType;
import com.rainbutler.domain.report.repository.ReportRepository;
import com.rainbutler.domain.user.entity.Role;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.global.config.PolicyProperties;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.storage.StorageService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 로그인 사용자의 현재 입양 목록을 조회합니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyAdoptionQueryService {

    private final AdoptionRepository adoptionRepository;
    private final UserRepository userRepository;
    private final ReportRepository reportRepository;
    private final PolicyProperties policyProperties;
    private final GrowthLevelPolicy growthLevelPolicy;
    private final DrainDisplayStatusPolicy drainDisplayStatusPolicy;
    private final StorageService storageService;
    private final Clock clock;

    /**
     * ACTIVE 입양만 최근 입양 순으로 조회합니다.
     *
     * <p>마지막 돌봄 시각은 입양 시각과 마지막 승인 점검 시각 중 늦은 값입니다. 자동 파양과 같은 정책값을
     * 사용해, 마지막 돌봄으로부터 경고 기준 일수(기본 28일)가 지난 경우 {@code releaseWarning}을 표시합니다.
     *
     * @param userId 로그인 사용자 id
     * @return 현재 입양 수 · 최대 입양 수 · 입양 목록
     */
    public MyAdoptionListResponse findMine(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now(clock);
        List<MyAdoptionResponse> adoptions = adoptionRepository
                .findAllByUser_IdAndStatusOrderByAdoptedAtDesc(user.getId(), AdoptionStatus.ACTIVE)
                .stream()
                .map(adoption -> toResponse(adoption, now))
                .toList();

        return new MyAdoptionListResponse(
                adoptions.size(), policyProperties.maxAdoptionsPerUser(), adoptions);
    }

    /**
     * 입양 상세를 조회합니다.
     *
     * <p>목록과 같은 돌봄 정보에 빗물받이 정보 · 입양일 · 다음 관리 예정일까지 남은 일수를 더합니다.
     *
     * @param userId 로그인 사용자 id
     * @param adoptionId 입양 id
     * @return 입양 상세
     * @throws BusinessException 사용자가 없으면(USER_NOT_FOUND), 입양이 없거나 해제됐으면
     *     (ADOPTION_NOT_FOUND), 입양자 · 관리자가 아니면(FORBIDDEN)
     */
    public AdoptionDetailResponse findDetail(Long userId, Long adoptionId) {
        User requester = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Adoption adoption = adoptionRepository.findById(adoptionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADOPTION_NOT_FOUND));
        if (adoption.getStatus() != AdoptionStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.ADOPTION_NOT_FOUND);
        }
        if (!isPrivileged(requester) && !adoption.getUser().getId().equals(requester.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return toDetail(adoption, LocalDateTime.now(clock));
    }

    /**
     * 점검 기록과 같은 기준으로 입양자 · 관리자만 볼 수 있게 합니다.
     */
    private boolean isPrivileged(User user) {
        return user.getRole() == Role.ADMIN || user.getRole() == Role.CITY_OFFICIAL;
    }

    private AdoptionDetailResponse toDetail(Adoption adoption, LocalDateTime now) {
        MyAdoptionResponse summary = toResponse(adoption, now);
        Drain drain = adoption.getDrain();
        return new AdoptionDetailResponse(
                summary.adoptionId(),
                summary.nickname(),
                adoption.getAdoptedAt(),
                summary.level(),
                summary.score(),
                summary.levelTitle(),
                summary.nextLevelScore(),
                summary.progressPercent(),
                summary.lastManagedAt(),
                summary.nextManagementAt(),
                daysUntil(now, summary.nextManagementAt()),
                summary.releaseWarning(),
                summary.missedForecastCount(),
                summary.lonely(),
                summary.displayStatus(),
                summary.displayStatusLabel(),
                new AdoptionDetailResponse.DrainInfo(
                        drain.getId(),
                        drain.getName(),
                        drain.getAddress(),
                        drain.getLatitude(),
                        drain.getLongitude(),
                        storageService.toReadUrl(drain.getPhotoUrl()),
                        drain.getDirtLevel(),
                        drain.isRisk()));
    }

    /** 다음 관리 예정일까지 남은 일수를 계산합니다. (예정일이 지났으면 음수) */
    private Integer daysUntil(LocalDateTime now, LocalDateTime target) {
        if (target == null) {
            return null;
        }
        return (int) ChronoUnit.DAYS.between(now.toLocalDate(), target.toLocalDate());
    }

    private MyAdoptionResponse toResponse(Adoption adoption, LocalDateTime now) {
        LocalDateTime lastManagedAt = lastManagedAt(adoption);
        boolean releaseWarning = !now.isBefore(
                lastManagedAt.plusDays(policyProperties.adoptionWarningDays()));
        int missedForecastCount = adoption.getIgnoreCount() == null ? 0 : adoption.getIgnoreCount();
        Drain drain = adoption.getDrain();
        boolean hasPendingBlockage = reportRepository.existsByDrain_IdAndTypeAndStatus(
                drain.getId(), ReportType.BLOCKAGE, ReportStatus.PENDING);
        DrainDisplayStatus displayStatus = drainDisplayStatusPolicy.resolve(
                adoption, hasPendingBlockage, now);
        int level = adoption.getLevel();
        int score = adoption.getScore();

        return new MyAdoptionResponse(
                adoption.getId(),
                adoption.getNickname(),
                drain.getId(),
                level,
                score,
                lastManagedAt,
                adoption.getNextManagementAt(),
                releaseWarning,
                missedForecastCount,
                missedForecastCount >= policyProperties.lonelyIgnoreCount(),
                drain.getAddress(),
                displayStatus,
                displayStatus.getLabel(),
                growthLevelPolicy.titleFor(level),
                growthLevelPolicy.nextLevelScore(level, score),
                growthLevelPolicy.progressPercent(level, score)
        );
    }

    private LocalDateTime lastManagedAt(Adoption adoption) {
        LocalDateTime adoptedAt = adoption.getAdoptedAt();
        LocalDateTime managedAt = adoption.getLastManagedAt();
        return managedAt != null && managedAt.isAfter(adoptedAt) ? managedAt : adoptedAt;
    }
}
