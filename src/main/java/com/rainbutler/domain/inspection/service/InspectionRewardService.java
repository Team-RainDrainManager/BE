package com.rainbutler.domain.inspection.service;

import com.rainbutler.domain.adoption.entity.Adoption;
import com.rainbutler.domain.adoption.repository.ManagementLogRepository;
import com.rainbutler.domain.adoption.service.ManagementCyclePolicy;
import com.rainbutler.domain.drain.service.DrainAiClassifier;
import com.rainbutler.domain.drain.service.GrowthLevelPolicy;
import com.rainbutler.domain.inspection.entity.Inspection;
import com.rainbutler.domain.inspection.entity.InspectionKind;
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
 * 점검 인증을 통과한 기록에 경험치와 성장 점수를 지급합니다.
 *
 * <p>경험치는 개인 참여 점수({@code point_history})와 빗물받이 성장({@code adoption.score})에 함께
 * 반영합니다. 금액은 그 주기 안에서 몇 번째 점검 · 청소인지로 정합니다. (한 주기는 입양 시점 기준
 * 14일 고정)
 *
 * <p>같은 사용자가 같은 빗물받이에서 하루에 점검 · 청소를 여러 번 해도 점수는 하루 한 번만 지급합니다.
 * 점검과 청소 중 먼저 인정된 쪽만 받고, 나머지는 0점입니다. 0점이 된 점검은 그 주기의 첫 점검 자리를
 * 쓰지 않아 다음 점검이 첫 점검 대우를 받습니다.
 *
 * <p>관리자 검토에서 승인된 기록도 같은 메서드를 쓰므로, 이미 지급한 기록은 다시 지급하지 않습니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InspectionRewardService {

    private final InspectionRepository inspectionRepository;
    private final ManagementLogRepository managementLogRepository;
    private final PointHistoryRepository pointHistoryRepository;
    private final GrowthLevelPolicy growthLevelPolicy;
    private final ManagementCyclePolicy managementCyclePolicy;
    private final ActivityRewardPolicy activityRewardPolicy;

    /**
     * 점검 한 건의 경험치 · 성장 점수를 지급합니다. 이미 지급한 기록이면 아무것도 하지 않습니다.
     *
     * @param inspection 인증을 통과한 점검 기록
     * @param growthAdoption 성장 점수를 받을 입양 (입양되지 않은 빗물받이의 대신 점검이면 null)
     * @param description 경험치 이력에 남길 설명
     * @param now 지급 시각 (하루 1회 · 주기 판정 기준)
     * @return 지급한 경험치 (하루 1회를 이미 채웠으면 0)
     */
    @Transactional
    public int reward(
            Inspection inspection, Adoption growthAdoption, String description, LocalDateTime now) {
        if (inspection.isRewarded()) {
            return inspection.getXpAwarded();
        }

        int xp = amount(inspection, growthAdoption, now);

        if (xp > 0) {
            pointHistoryRepository.save(PointHistory.builder()
                    .user(inspection.getUser())
                    .sourceType(PointSourceType.INSPECTION)
                    .sourceId(inspection.getId())
                    .amount(xp)
                    .type(PointType.EARN)
                    .description(description)
                    .build());
            inspection.getUser().addPoint(xp);

            // 대신 점검은 맡은 사람의 개인 포인트만 준다. 남의 빗물받이 성장 점수는 올리지 않는다.
            if (growthAdoption != null && inspection.getKind() != InspectionKind.SUBSTITUTE) {
                growthAdoption.addGrowth(
                        xp, growthLevelPolicy.levelFor(growthAdoption.getScore() + xp));
            }
        }

        inspection.reward(xp);
        return xp;
    }

    /**
     * 이 점검에 지급할 경험치를 정합니다.
     *
     * <p>그날 같은 빗물받이에서 이미 점수를 받았으면 0입니다. 점검은 청소 없이 끝난 경우(괜찮음)와
     * 청소까지 인정된 경우로 나눠 금액을 정합니다.
     */
    private int amount(Inspection inspection, Adoption growthAdoption, LocalDateTime now) {
        if (rewardedToday(inspection, now)) {
            return 0;
        }

        int beforeGrade = inspection.getBeforeGrade();
        if (inspection.getKind() == InspectionKind.SUBSTITUTE) {
            // 대신 점검은 주기와 관계없이 고정 금액
            return activityRewardPolicy.substituteXp(!DrainAiClassifier.isClean(beforeGrade));
        }

        Adoption adoption = inspection.getAdoption() != null
                ? inspection.getAdoption()
                : growthAdoption;
        if (DrainAiClassifier.isClean(beforeGrade)) {
            return activityRewardPolicy.visitXp(visitRewardsInCycle(inspection, adoption, now));
        }

        int afterGrade = inspection.getAfterGrade() == null
                ? beforeGrade
                : inspection.getAfterGrade();
        long cleanings = cleaningRewardsInCycle(inspection, adoption, now);
        return activityRewardPolicy.cleaningXp(cleanings, beforeGrade - afterGrade);
    }

    /** 그날 같은 사용자 · 빗물받이로 이미 점수를 받았는지 확인합니다. */
    private boolean rewardedToday(Inspection inspection, LocalDateTime now) {
        LocalDateTime dayStart = now.toLocalDate().atStartOfDay();
        return pointHistoryRepository.countDrainRewardsOnDay(
                inspection.getUser().getId(),
                inspection.getDrain().getId(),
                dayStart,
                dayStart.plusDays(1)) > 0;
    }

    /** 그 주기 안에서 이미 경험치를 받은 점검 수를 셉니다. (점검 · 관리 기록 경로 합산) */
    private long visitRewardsInCycle(
            Inspection inspection, Adoption adoption, LocalDateTime now) {
        if (adoption == null) {
            return 0;
        }
        LocalDateTime start = managementCyclePolicy.cycleStart(adoption.getAdoptedAt(), now);
        LocalDateTime end = start.plusDays(managementCyclePolicy.cycleDays());
        Long userId = inspection.getUser().getId();
        Long drainId = inspection.getDrain().getId();
        return inspectionRepository.countVisitRewards(userId, drainId, start, end)
                + managementLogRepository.countVisitRewards(userId, drainId, start, end);
    }

    /** 그 주기 안에서 이미 경험치를 받은 청소 수를 셉니다. (점검 · 관리 기록 경로 합산) */
    private long cleaningRewardsInCycle(
            Inspection inspection, Adoption adoption, LocalDateTime now) {
        if (adoption == null) {
            return 0;
        }
        LocalDateTime start = managementCyclePolicy.cycleStart(adoption.getAdoptedAt(), now);
        LocalDateTime end = start.plusDays(managementCyclePolicy.cycleDays());
        Long userId = inspection.getUser().getId();
        Long drainId = inspection.getDrain().getId();
        return inspectionRepository.countCleaningRewards(userId, drainId, start, end)
                + managementLogRepository.countCleaningRewards(userId, drainId, start, end);
    }
}
