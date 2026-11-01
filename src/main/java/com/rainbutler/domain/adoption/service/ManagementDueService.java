package com.rainbutler.domain.adoption.service;

import com.rainbutler.domain.adoption.entity.Adoption;
import com.rainbutler.domain.adoption.entity.AdoptionStatus;
import com.rainbutler.domain.adoption.repository.AdoptionRepository;
import com.rainbutler.domain.notification.entity.NotificationType;
import com.rainbutler.domain.push.service.PushService;
import com.rainbutler.domain.user.service.PauseService;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리 예정일 하루 전 알림을 보냅니다. (매일 배치)
 *
 * <p>다음 관리 예정일이 내일인 {@code ACTIVE} 입양의 입양자에게 한 번 알립니다. 점검 쉬기 중인 사용자는
 * 건너뜁니다. 같은 관리 주기에서 두 번 알리지 않도록 알림을 보낸 시각을 입양에 남깁니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ManagementDueService {

    private final AdoptionRepository adoptionRepository;
    private final PushService pushService;
    private final PauseService pauseService;

    /**
     * 입양 한 건의 관리 예정일 하루 전 알림을 보냅니다.
     *
     * @param adoptionId 입양 id
     * @param now 현재 시각
     * @return 알림을 보냈으면 true
     */
    @Transactional
    public boolean notifyDue(Long adoptionId, LocalDateTime now) {
        Adoption adoption = adoptionRepository.findById(adoptionId).orElse(null);
        if (adoption == null || adoption.getStatus() != AdoptionStatus.ACTIVE) {
            return false;
        }

        LocalDateTime nextManagementAt = adoption.getNextManagementAt();
        if (nextManagementAt == null
                || !nextManagementAt.toLocalDate().equals(now.toLocalDate().plusDays(1))) {
            return false;
        }

        if (pauseService.isCareSuspended(adoption.getUser()) || adoption.isManagementNotified()) {
            return false;
        }

        adoption.markManagementNotified(now);
        pushService.notify(adoption.getUser().getId(), NotificationType.MANAGEMENT_DUE,
                "빗물받이 관리 예정일이에요",
                adoption.getNickname() + "을(를) 내일 점검할 차례예요. "
                        + "잊지 말고 상태를 살펴봐 주세요.",
                null, adoption.getId(), data(adoption));
        return true;
    }

    private Map<String, String> data(Adoption adoption) {
        return Map.of(
                "adoptionId", String.valueOf(adoption.getId()),
                "drainId", String.valueOf(adoption.getDrain().getId()),
                "nextManagementAt", adoption.getNextManagementAt().toString());
    }
}
