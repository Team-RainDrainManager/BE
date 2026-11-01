package com.rainbutler.domain.adoption.service;

import com.rainbutler.domain.adoption.entity.Adoption;
import com.rainbutler.domain.adoption.entity.AdoptionStatus;
import com.rainbutler.domain.adoption.entity.ReleaseReason;
import com.rainbutler.domain.adoption.repository.AdoptionRepository;
import com.rainbutler.domain.notification.entity.NotificationType;
import com.rainbutler.domain.push.service.PushService;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.service.PauseService;
import com.rainbutler.global.config.PolicyProperties;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 입양의 미관리 경고 · 자동 파양을 처리합니다. (매일 배치)
 *
 * <ul>
 *   <li>관리 기준 시각(입양일 · 마지막 관리일 · 지난 쉬기 종료일 중 가장 늦은 시각)에서
 *       경고 일수(기본 28일)가 지나면 경고 알림을 한 번 보냅니다.</li>
 *   <li>해제 일수(기본 35일)가 지나면 입양을 자동으로 해제합니다.</li>
 *   <li>점검 쉬기 중인 사용자는 건너뜁니다.</li>
 * </ul>
 *
 * <p>자동 파양도 직접 파양과 같게 입양 행을 {@code RELEASED}로 남겨 기록 · 성장을 보관하고, 입양자에게 간
 * {@code SENT} · {@code ACCEPTED} 요청은 소유자를 비우고 {@code OPEN}으로 다시 공개합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdoptionCareService {

    private final AdoptionRepository adoptionRepository;
    private final AdoptionService adoptionService;
    private final PushService pushService;
    private final PauseService pauseService;
    private final PolicyProperties policyProperties;

    /**
     * 입양 한 건의 미관리 경고 · 자동 파양을 판정하고 처리합니다.
     *
     * @param adoptionId 입양 id
     * @param now 현재 시각
     * @return 처리 결과
     */
    @Transactional
    public AdoptionCareResult care(Long adoptionId, LocalDateTime now) {
        Adoption adoption = adoptionRepository.findById(adoptionId).orElse(null);
        if (adoption == null || adoption.getStatus() != AdoptionStatus.ACTIVE) {
            return AdoptionCareResult.NONE;
        }

        User user = adoption.getUser();
        if (pauseService.isCareSuspended(user)) {
            return AdoptionCareResult.NONE;
        }

        LocalDateTime baseline = pauseService.careBaseline(adoption, user);

        // 해제일이 지났으면 경고보다 먼저 자동 파양한다 (배치가 오래 밀려 경고 · 해제가 겹친 경우)
        if (!now.isBefore(baseline.plusDays(policyProperties.adoptionReleaseDays()))) {
            release(adoption, now);
            return AdoptionCareResult.RELEASED;
        }

        if (!now.isBefore(baseline.plusDays(policyProperties.adoptionWarningDays()))
                && !adoption.isWarningSentSince(baseline)) {
            warn(adoption, now);
            return AdoptionCareResult.WARNED;
        }

        return AdoptionCareResult.NONE;
    }

    private void warn(Adoption adoption, LocalDateTime now) {
        adoption.markWarned(now);
        int daysUntilRelease = policyProperties.adoptionReleaseDays()
                - policyProperties.adoptionWarningDays();
        pushService.notify(adoption.getUser().getId(), NotificationType.ADOPTION_WARNING,
                "빗물받이 점검이 필요해요",
                adoption.getNickname() + "을(를) " + policyProperties.adoptionWarningDays()
                        + "일째 점검하지 않았어요. " + daysUntilRelease
                        + "일 뒤 입양이 자동으로 해제돼요.",
                null, adoption.getId(), data(adoption));
    }

    private void release(Adoption adoption, LocalDateTime now) {
        adoptionService.release(adoption, ReleaseReason.AUTO, now);

        pushService.notify(adoption.getUser().getId(), NotificationType.ADOPTION_RELEASED,
                "입양이 자동으로 해제됐어요",
                adoption.getNickname() + "을(를) " + policyProperties.adoptionReleaseDays()
                        + "일 동안 점검하지 않아 입양이 해제됐어요. 다른 집사가 다시 입양할 수 있어요.",
                null, adoption.getId(), data(adoption));
    }

    private Map<String, String> data(Adoption adoption) {
        return Map.of(
                "adoptionId", String.valueOf(adoption.getId()),
                "drainId", String.valueOf(adoption.getDrain().getId()));
    }
}
