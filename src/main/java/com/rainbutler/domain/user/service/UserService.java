package com.rainbutler.domain.user.service;

import com.rainbutler.domain.adoption.entity.AdoptionStatus;
import com.rainbutler.domain.adoption.entity.ManagementActionType;
import com.rainbutler.domain.adoption.repository.AdoptionRepository;
import com.rainbutler.domain.cleaning.entity.CleaningType;
import com.rainbutler.domain.point.repository.PointHistoryRepository;
import com.rainbutler.domain.sanction.service.SanctionService;
import com.rainbutler.domain.user.dto.MeResponse;
import com.rainbutler.domain.user.dto.NicknameUpdateRequest;
import com.rainbutler.domain.user.dto.NicknameUpdateResponse;
import com.rainbutler.domain.user.dto.PauseStatusResponse;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.ActivityStatsRepository;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.global.config.PolicyProperties;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 정보를 조회합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PointHistoryRepository pointHistoryRepository;
    private final AdoptionRepository adoptionRepository;
    private final SanctionService sanctionService;
    private final PauseService pauseService;
    private final ActivityStatsRepository activityStatsRepository;
    private final PolicyProperties policyProperties;

    /**
     * 로그인 사용자의 정보를 조회합니다.
     *
     * @param userId 로그인 사용자 id
     * @return 내 정보
     * @throws BusinessException 사용자가 없을 때 (USER_NOT_FOUND)
     */
    public MeResponse getMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        long participationScore = pointHistoryRepository.sumParticipationScore(userId);
        if (user.getPoint() != null && user.getPoint() != participationScore) {
            // 포인트 이력이 기준이다. 둘이 다르면 적립 · 회수 처리 어딘가에서 users.point 갱신이 빠진 것
            log.warn("[포인트 불일치] userId={} users.point={} 이력 합={}",
                    userId, user.getPoint(), participationScore);
        }
        long adoptionCount =
                adoptionRepository.countByUser_IdAndStatus(userId, AdoptionStatus.ACTIVE);

        MeResponse.SanctionSummary sanction = sanctionService.findActive(userId)
                .map(active -> new MeResponse.SanctionSummary(
                        true, active.getUntil(), active.isPermanent()))
                .orElse(MeResponse.SanctionSummary.NONE);
        PauseStatusResponse pauseStatus = pauseService.getStatus(userId);
        MeResponse.PauseSummary pause = new MeResponse.PauseSummary(
                pauseStatus.activeUntil(), pauseStatus.nextAvailableAt());

        // 점검 수: 대신 점검(청소) + 입양 후 점검(살펴보기 · 청소 모두)
        long inspectionCount = activityStatsRepository.countCompletedCleanings(
                userId, List.of(CleaningType.SUBSTITUTE))
                + activityStatsRepository.countVerifiedManagements(
                        userId, List.of(ManagementActionType.values()));
        // 봉사 시간: 청소한 횟수(입양 전 · 대신 점검 · 점검 중 청소) × 1회당 분
        long cleaningCount = activityStatsRepository.countCompletedCleanings(
                userId, List.of(CleaningType.values()))
                + activityStatsRepository.countVerifiedManagements(
                        userId, List.of(ManagementActionType.CLEANING));
        long volunteerMinutes = cleaningCount * policyProperties.volunteerMinutesPerCleaning();

        return MeResponse.of(user, participationScore, adoptionCount, sanction, pause,
                inspectionCount, volunteerMinutes);
    }

    /**
     * 로그인 사용자의 닉네임을 바꿉니다. 중복을 허용하고, 같은 닉네임으로 바꿔도 성공합니다.
     *
     * @param userId 로그인 사용자 id
     * @param request 새 닉네임 (검증 · 앞뒤 공백 제거 완료)
     * @return 바뀐 닉네임과 수정 시각
     * @throws BusinessException 사용자가 없을 때 (USER_NOT_FOUND)
     */
    @Transactional
    public NicknameUpdateResponse updateNickname(Long userId, NicknameUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.changeNickname(request.nickname());
        // 수정 시각(Auditing)이 응답에 담기도록 먼저 반영한다
        userRepository.flush();
        return NicknameUpdateResponse.from(user);
    }
}
