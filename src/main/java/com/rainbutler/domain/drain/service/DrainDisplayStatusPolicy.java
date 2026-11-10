package com.rainbutler.domain.drain.service;

import com.rainbutler.domain.adoption.entity.Adoption;
import com.rainbutler.domain.drain.entity.DrainDisplayStatus;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

/**
 * 빗물받이 표시 상태를 계산합니다.
 *
 * <p>화면의 필터와 같은 기준을 씁니다. 막힘 제보가 가장 먼저 보이고, 입양되지 않은 빗물받이는 미입양,
 * 입양된 빗물받이는 다음 점검일이 지났는지에 따라 점검 지연 · 정상으로 나뉩니다.
 */
@Component
public class DrainDisplayStatusPolicy {

    /**
     * 표시 상태를 계산합니다.
     *
     * @param activeAdoption ACTIVE 입양 (없으면 null)
     * @param hasBlockageReport 표시할 막힘 제보가 있는지 (처리 대기 중이거나, 승인 후 해제 전)
     * @param now 기준 시각
     * @return 표시 상태
     */
    public DrainDisplayStatus resolve(
            Adoption activeAdoption, boolean hasBlockageReport, LocalDateTime now) {
        if (hasBlockageReport) {
            return DrainDisplayStatus.BLOCKAGE_REPORTED;
        }
        if (activeAdoption == null) {
            return DrainDisplayStatus.AVAILABLE;
        }
        LocalDateTime nextManagementAt = activeAdoption.getNextManagementAt();
        if (nextManagementAt != null && nextManagementAt.isBefore(now)) {
            return DrainDisplayStatus.MANAGEMENT_DELAYED;
        }
        return DrainDisplayStatus.NORMAL;
    }
}
