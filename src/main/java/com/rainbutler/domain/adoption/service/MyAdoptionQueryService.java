package com.rainbutler.domain.adoption.service;

import com.rainbutler.domain.adoption.dto.Response.MyAdoptionListResponse;
import com.rainbutler.domain.adoption.dto.Response.MyAdoptionResponse;
import com.rainbutler.domain.adoption.entity.Adoption;
import com.rainbutler.domain.adoption.entity.AdoptionStatus;
import com.rainbutler.domain.adoption.repository.AdoptionRepository;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.global.config.PolicyProperties;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
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
    private final PolicyProperties policyProperties;
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

    private MyAdoptionResponse toResponse(Adoption adoption, LocalDateTime now) {
        LocalDateTime lastManagedAt = lastManagedAt(adoption);
        boolean releaseWarning = !now.isBefore(
                lastManagedAt.plusDays(policyProperties.adoptionWarningDays()));
        int missedForecastCount = adoption.getIgnoreCount() == null ? 0 : adoption.getIgnoreCount();

        return new MyAdoptionResponse(
                adoption.getId(),
                adoption.getNickname(),
                adoption.getDrain().getId(),
                adoption.getLevel(),
                adoption.getScore(),
                lastManagedAt,
                adoption.getNextManagementAt(),
                releaseWarning,
                missedForecastCount,
                missedForecastCount >= policyProperties.lonelyIgnoreCount());
    }

    private LocalDateTime lastManagedAt(Adoption adoption) {
        LocalDateTime adoptedAt = adoption.getAdoptedAt();
        LocalDateTime managedAt = adoption.getLastManagedAt();
        return managedAt != null && managedAt.isAfter(adoptedAt) ? managedAt : adoptedAt;
    }
}
