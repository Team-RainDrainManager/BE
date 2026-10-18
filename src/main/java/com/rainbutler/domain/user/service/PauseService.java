package com.rainbutler.domain.user.service;

import com.rainbutler.domain.adoption.entity.Adoption;
import com.rainbutler.domain.adoption.entity.AdoptionStatus;
import com.rainbutler.domain.adoption.repository.AdoptionRepository;
import com.rainbutler.domain.user.dto.PauseResponse;
import com.rainbutler.domain.user.dto.PauseStatusResponse;
import com.rainbutler.domain.user.dto.PauseUnavailableReason;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.global.config.PolicyProperties;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 점검 쉬기를 조회 · 신청합니다.
 *
 * <p>입양자는 14일(기본) 동안 정기 점검을 쉴 수 있습니다. 쉬는 동안 정기 점검 주기 · 미관리 경고 · 자동 해제는
 * 보류되고, 비 예보 점검 요청은 계속 옵니다. 90일(기본)에 한 번, 취소 · 연장은 없습니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PauseService {

    private final UserRepository userRepository;
    private final AdoptionRepository adoptionRepository;
    private final OnboardingGuard onboardingGuard;
    private final PolicyProperties policyProperties;
    private final Clock clock;

    /**
     * 점검 쉬기 상태를 조회합니다.
     *
     * @param userId 로그인 사용자 id
     * @return 신청 가능 여부 · 쉬는 중 종료 시각 · 다음 신청 가능 시각 · 불가 사유
     */
    public PauseStatusResponse getStatus(Long userId) {
        User user = findUser(userId);
        LocalDateTime now = now();
        PauseUnavailableReason reason = unavailableReason(user, now);
        LocalDateTime activeUntil = user.isPaused(now) ? user.getPauseUntil() : null;
        LocalDateTime nextAvailableAt = inCooldown(user, now) ? nextAvailableAt(user) : null;
        return new PauseStatusResponse(reason == null, activeUntil, nextAvailableAt, reason);
    }

    /**
     * 점검 쉬기를 신청합니다. 입양 중인 빗물받이의 다음 관리 예정일을 쉬기 종료 뒤로 미룹니다.
     *
     * @param userId 로그인 사용자 id
     * @return 쉬기 종료 시각
     * @throws BusinessException 온보딩 미완료(403 ONBOARDING_REQUIRED) · 쉬는 중(409 PAUSE_ALREADY_ACTIVE) ·
     *         입양 없음(409 PAUSE_NO_ADOPTION) · 쿨다운(409 PAUSE_COOLDOWN, details.nextAvailableAt)
     */
    @Transactional
    public PauseResponse apply(Long userId) {
        onboardingGuard.require(userId);
        User user = findUser(userId);
        LocalDateTime now = now();

        PauseUnavailableReason reason = unavailableReason(user, now);
        if (reason == PauseUnavailableReason.ALREADY_ACTIVE) {
            throw new BusinessException(ErrorCode.PAUSE_ALREADY_ACTIVE);
        }
        if (reason == PauseUnavailableReason.NO_ADOPTION) {
            throw new BusinessException(ErrorCode.PAUSE_NO_ADOPTION);
        }
        if (reason == PauseUnavailableReason.COOLDOWN) {
            throw new BusinessException(ErrorCode.PAUSE_COOLDOWN,
                    Map.of("nextAvailableAt", nextAvailableAt(user)));
        }

        user.startPause(now, policyProperties.pauseDays());
        // 쉬는 동안 정기 점검이 돌아오지 않도록 다음 관리 예정일을 쉬기 종료 뒤로 미룬다
        activeAdoptions(userId)
                .forEach(adoption -> adoption.postponeNextManagement(user.getPauseUntil()));
        return new PauseResponse(user.getPauseUntil());
    }

    /**
     * 신청할 수 없는 이유를 우선순위대로 찾습니다. (쉬는 중 → 입양 없음 → 쿨다운)
     */
    private PauseUnavailableReason unavailableReason(User user, LocalDateTime now) {
        if (user.isPaused(now)) {
            return PauseUnavailableReason.ALREADY_ACTIVE;
        }
        if (activeAdoptions(user.getId()).isEmpty()) {
            return PauseUnavailableReason.NO_ADOPTION;
        }
        if (inCooldown(user, now)) {
            return PauseUnavailableReason.COOLDOWN;
        }
        return null;
    }

    /**
     * 마지막 신청 후 쿨다운이 지나지 않았는지 확인합니다. (다음 신청 가능 시각 정각부터 신청 가능)
     */
    private boolean inCooldown(User user, LocalDateTime now) {
        LocalDateTime next = nextAvailableAt(user);
        return next != null && now.isBefore(next);
    }

    private LocalDateTime nextAvailableAt(User user) {
        return user.nextPauseAvailableAt(policyProperties.restCooldownDays());
    }

    private List<Adoption> activeAdoptions(Long userId) {
        return adoptionRepository.findAllByUser_IdAndStatus(userId, AdoptionStatus.ACTIVE);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
