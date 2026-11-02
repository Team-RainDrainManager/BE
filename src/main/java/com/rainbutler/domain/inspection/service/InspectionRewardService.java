package com.rainbutler.domain.inspection.service;

import com.rainbutler.domain.adoption.entity.Adoption;
import com.rainbutler.domain.drain.service.GrowthLevelPolicy;
import com.rainbutler.domain.inspection.entity.Inspection;
import com.rainbutler.domain.inspection.repository.InspectionRepository;
import com.rainbutler.domain.point.entity.PointHistory;
import com.rainbutler.domain.point.entity.PointSourceType;
import com.rainbutler.domain.point.entity.PointType;
import com.rainbutler.domain.point.repository.PointHistoryRepository;
import com.rainbutler.global.config.PolicyProperties;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 점검 인증을 통과한 기록에 경험치와 성장 점수를 지급합니다.
 *
 * <p>경험치는 개인 참여 점수({@code point_history})와 빗물받이 성장({@code adoption.score})에 함께
 * 반영합니다. 같은 사용자가 같은 빗물받이를 하루에 여러 번 점검해도 경험치는 하루 한 번만 지급합니다.
 *
 * <p>관리자 검토에서 승인된 기록도 같은 메서드를 쓰므로, 이미 지급한 기록은 다시 지급하지 않습니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InspectionRewardService {

    private final InspectionRepository inspectionRepository;
    private final PointHistoryRepository pointHistoryRepository;
    private final GrowthLevelPolicy growthLevelPolicy;
    private final PolicyProperties policyProperties;

    /**
     * 점검 한 건의 경험치 · 성장 점수를 지급합니다. 이미 지급한 기록이면 아무것도 하지 않습니다.
     *
     * @param inspection 인증을 통과한 점검 기록
     * @param growthAdoption 성장 점수를 받을 입양 (입양되지 않은 빗물받이의 대신 점검이면 null)
     * @param description 경험치 이력에 남길 설명
     * @param now 지급 시각 (하루 1회 판정 기준)
     * @return 지급한 경험치 (하루 1회를 이미 채웠으면 0)
     */
    @Transactional
    public int reward(
            Inspection inspection, Adoption growthAdoption, String description, LocalDateTime now) {
        if (inspection.isRewarded()) {
            return inspection.getXpAwarded();
        }

        LocalDate today = now.toLocalDate();
        long rewardedToday = inspectionRepository.countRewardedBetween(
                inspection.getUser().getId(),
                inspection.getDrain().getId(),
                today.atStartOfDay(),
                today.plusDays(1).atStartOfDay());
        int xp = rewardedToday == 0 ? policyProperties.inspectionXp() : 0;

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

            if (growthAdoption != null) {
                growthAdoption.addGrowth(
                        xp, growthLevelPolicy.levelFor(growthAdoption.getScore() + xp));
            }
        }

        inspection.reward(xp);
        return xp;
    }
}
