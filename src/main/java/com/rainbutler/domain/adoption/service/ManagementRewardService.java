package com.rainbutler.domain.adoption.service;

import com.rainbutler.domain.adoption.entity.Adoption;
import com.rainbutler.domain.adoption.entity.ManagementActionType;
import com.rainbutler.domain.adoption.entity.ManagementLog;
import com.rainbutler.domain.adoption.repository.ManagementLogRepository;
import com.rainbutler.domain.drain.service.GrowthLevelPolicy;
import com.rainbutler.domain.inspection.repository.InspectionRepository;
import com.rainbutler.domain.point.entity.PointHistory;
import com.rainbutler.domain.point.entity.PointSourceType;
import com.rainbutler.domain.point.entity.PointType;
import com.rainbutler.domain.point.repository.PointHistoryRepository;
import com.rainbutler.domain.point.service.ActivityRewardPolicy;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 입양 후 관리 기록(점검 · 청소)에 경험치와 성장 점수를 지급합니다.
 *
 * <p>레거시 관리 기록 API용입니다. 점검 · 청소 공통 제출({@code InspectionService})과 같은 리워드
 * 정책을 따릅니다. 개인 참여 점수와 빗물받이 성장 점수에 같은 값을 넣고, 같은 빗물받이는 하루에 한
 * 번만 지급합니다. 같은 기록에 두 번 지급하지 않도록 {@code point_history}에 이미 남았는지 먼저
 * 확인합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ManagementRewardService {

    private static final String DESCRIPTION = "입양 관리";

    private final ManagementLogRepository managementLogRepository;
    private final InspectionRepository inspectionRepository;
    private final PointHistoryRepository pointHistoryRepository;
    private final GrowthLevelPolicy growthLevelPolicy;
    private final ManagementCyclePolicy managementCyclePolicy;
    private final ActivityRewardPolicy activityRewardPolicy;

    /**
     * 인정된 관리 기록에 점수를 지급합니다. 이미 지급했으면 아무것도 하지 않습니다.
     *
     * @param log 인정된 관리 기록
     * @param now 지급 시각 (하루 1회 · 주기 판정 기준)
     * @return 지급한 점수 (이미 지급했거나 하루 1회를 채웠으면 0)
     */
    @Transactional
    public int reward(ManagementLog log, LocalDateTime now) {
        if (pointHistoryRepository.existsBySourceTypeAndSourceId(
                PointSourceType.MANAGEMENT, log.getId())) {
            return 0;
        }

        int xp = amount(log, now);

        if (xp > 0) {
            Adoption adoption = log.getAdoption();
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

    /**
     * 이 관리 기록에 지급할 경험치를 정합니다.
     *
     * <p>그날 같은 빗물받이에서 이미 점수를 받았으면 0입니다. 점검 기록은 점검 금액, 청소 기록은
     * 하락 단계에 따른 청소 금액을 받습니다.
     */
    private int amount(ManagementLog log, LocalDateTime now) {
        Adoption adoption = log.getAdoption();
        Long userId = adoption.getUser().getId();
        Long drainId = adoption.getDrain().getId();

        LocalDateTime dayStart = now.toLocalDate().atStartOfDay();
        if (pointHistoryRepository.countDrainRewardsOnDay(
                userId, drainId, dayStart, dayStart.plusDays(1)) > 0) {
            return 0;
        }

        LocalDateTime start = managementCyclePolicy.cycleStart(adoption.getAdoptedAt(), now);
        LocalDateTime end = start.plusDays(managementCyclePolicy.cycleDays());

        if (log.getActionType() == ManagementActionType.INSPECTION) {
            long visits = inspectionRepository.countVisitRewards(userId, drainId, start, end)
                    + managementLogRepository.countVisitRewards(userId, drainId, start, end);
            return activityRewardPolicy.visitXp(visits);
        }

        long cleanings = inspectionRepository.countCleaningRewards(userId, drainId, start, end)
                + managementLogRepository.countCleaningRewards(userId, drainId, start, end);
        int before = log.getDirtLevel() == null ? 1 : log.getDirtLevel();
        int after = log.getAfterDirtLevel() == null ? before : log.getAfterDirtLevel();
        return activityRewardPolicy.cleaningXp(cleanings, before - after);
    }
}
