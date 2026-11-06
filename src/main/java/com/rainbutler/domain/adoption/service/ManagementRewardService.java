package com.rainbutler.domain.adoption.service;

import com.rainbutler.domain.adoption.entity.Adoption;
import com.rainbutler.domain.adoption.entity.ManagementLog;
import com.rainbutler.domain.adoption.repository.ManagementLogRepository;
import com.rainbutler.domain.drain.service.GrowthLevelPolicy;
import com.rainbutler.domain.point.entity.PointHistory;
import com.rainbutler.domain.point.entity.PointSourceType;
import com.rainbutler.domain.point.entity.PointType;
import com.rainbutler.domain.point.repository.PointHistoryRepository;
import com.rainbutler.global.config.PolicyProperties;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 입양 후 관리 기록(점검 · 청소)에 경험치와 성장 점수를 지급합니다.
 *
 * <p>개인 참여 점수와 빗물받이 성장 점수에 같은 값을 넣고, 같은 빗물받이는 하루에 한 번만 지급합니다.
 * 같은 기록에 두 번 지급하지 않도록 {@code point_history}에 이미 남았는지 먼저 확인합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ManagementRewardService {

    private static final String DESCRIPTION = "입양 점검";

    private final ManagementLogRepository managementLogRepository;
    private final PointHistoryRepository pointHistoryRepository;
    private final GrowthLevelPolicy growthLevelPolicy;
    private final PolicyProperties policyProperties;

    /**
     * 인정된 관리 기록에 점수를 지급합니다. 이미 지급했으면 아무것도 하지 않습니다.
     *
     * @param log 인정된 관리 기록
     * @param now 지급 시각 (하루 1회 판정 기준)
     * @return 지급한 점수 (이미 지급했거나 하루 1회를 채웠으면 0)
     */
    @Transactional
    public int reward(ManagementLog log, LocalDateTime now) {
        if (pointHistoryRepository.existsBySourceTypeAndSourceId(
                PointSourceType.MANAGEMENT, log.getId())) {
            return 0;
        }

        Adoption adoption = log.getAdoption();
        LocalDateTime dayStart = now.toLocalDate().atStartOfDay();
        long rewardedToday = managementLogRepository.countVerifiedOnDay(
                adoption.getUser().getId(),
                adoption.getDrain().getId(),
                log.getId(),
                dayStart,
                dayStart.plusDays(1));
        int xp = rewardedToday == 0 ? policyProperties.inspectionXp() : 0;

        if (xp > 0) {
            pointHistoryRepository.save(PointHistory.builder()
                    .user(adoption.getUser())
                    .sourceType(PointSourceType.MANAGEMENT)
                    .sourceId(log.getId())
                    .amount(xp)
                    .type(PointType.EARN)
                    .description(DESCRIPTION)
                    .build());
            adoption.getUser().addPoint(xp);
            adoption.addGrowth(xp, growthLevelPolicy.levelFor(adoption.getScore() + xp));
        }
        return xp;
    }
}
