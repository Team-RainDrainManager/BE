package com.rainbutler.domain.sanction.service;

import com.rainbutler.domain.report.entity.Report;
import com.rainbutler.domain.report.repository.ReportRepository;
import com.rainbutler.domain.sanction.entity.Sanction;
import com.rainbutler.domain.sanction.repository.SanctionRepository;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.global.config.PolicyProperties;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 제재를 조회 · 부과합니다.
 *
 * <p>다른 기능에서 제재 중인 사용자를 막을 때 (점검 제출 · 대신 점검 수락 등):
 * <pre>
 * sanctionService.requireNotSanctioned(userId);   // 제재 중이면 403 USER_SANCTIONED
 * </pre>
 * 관리자 제재 부과는 {@link #impose}를 그대로 씁니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SanctionService {

    /** 영구 제재가 가장 늦게 끝나고, 그다음은 종료 시각이 늦은 순 */
    private static final Comparator<Sanction> LATEST_END = Comparator
            .comparing(Sanction::isPermanent)
            .thenComparing(Sanction::getUntil, Comparator.nullsLast(Comparator.naturalOrder()));

    private final SanctionRepository sanctionRepository;
    private final UserRepository userRepository;
    private final ReportRepository reportRepository;
    private final PolicyProperties policyProperties;
    private final Clock clock;

    /**
     * 지금 효력이 있는 제재를 찾습니다. 여러 개면 가장 늦게 끝나는 것(영구 우선)을 돌려줍니다.
     *
     * @param userId 사용자 id
     * @return 효력 있는 제재
     */
    public Optional<Sanction> findActive(Long userId) {
        LocalDateTime now = now();
        return sanctionRepository.findAllByUser_IdAndRevokedAtIsNull(userId).stream()
                .filter(sanction -> sanction.isActive(now))
                .max(LATEST_END);
    }

    /**
     * 지금 제재 중인지 확인합니다.
     *
     * @param userId 사용자 id
     * @return 제재 중 여부
     */
    public boolean isSanctioned(Long userId) {
        return findActive(userId).isPresent();
    }

    /**
     * 제재 중이면 막습니다. 다른 기능의 서비스 첫 줄에서 부릅니다.
     *
     * @param userId 사용자 id
     * @throws BusinessException 제재 중 (USER_SANCTIONED 403)
     */
    public void requireNotSanctioned(Long userId) {
        if (isSanctioned(userId)) {
            throw new BusinessException(ErrorCode.USER_SANCTIONED);
        }
    }

    /**
     * 제재를 부과합니다. 해제되지 않은 기존 제재 수 + 1로 단계를 정합니다.
     *
     * <ul>
     *   <li>1회째 {@code sanction-first-days}일(기본 7일), 2회째 {@code sanction-second-days}일(기본 14일),
     *   {@code sanction-permanent-count}회째(기본 3회)부터 영구</li>
     *   <li>이미 제재 중이면 기존 종료 시각부터 이어서 시작한다</li>
     * </ul>
     *
     * @param userId 제재 대상
     * @param reportId 원인 제보 (없으면 null)
     * @param reason 사유 (사용자에게 보임)
     * @param adminId 부과한 관리자 (없으면 null)
     * @return 부과한 제재
     * @throws BusinessException 사용자 · 관리자 없음 (USER_NOT_FOUND), 제보 없음 (REPORT_NOT_FOUND)
     */
    @Transactional
    public Sanction impose(Long userId, Long reportId, String reason, Long adminId) {
        User user = findUser(userId);
        Report report = reportId == null ? null : reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
        User admin = adminId == null ? null : findUser(adminId);

        LocalDateTime now = now();
        long count = sanctionRepository.countByUser_IdAndRevokedAtIsNull(userId) + 1;
        LocalDateTime startsAt = nextStart(userId, now);

        Sanction sanction;
        if (count >= policyProperties.sanctionPermanentCount()) {
            sanction = Sanction.permanent(user, report, admin, reason, startsAt);
        } else {
            int days = count == 1
                    ? policyProperties.sanctionFirstDays() : policyProperties.sanctionSecondDays();
            sanction = Sanction.temporary(user, report, admin, reason, startsAt,
                    startsAt.plusDays(days));
        }
        return sanctionRepository.save(sanction);
    }

    /**
     * 제재 이력을 최신순으로 조회합니다. (해제된 제재 포함)
     *
     * @param userId 사용자 id
     * @return 제재 이력
     */
    public List<Sanction> findHistory(Long userId) {
        return sanctionRepository.findAllByUser_IdOrderByStartsAtDescIdDesc(userId);
    }

    /**
     * 새 제재의 시작 시각입니다. 아직 끝나지 않은 제재(시작 전 포함)가 있으면 그 종료 시각부터 이어서 시작합니다.
     */
    private LocalDateTime nextStart(Long userId, LocalDateTime now) {
        List<Sanction> unfinished = sanctionRepository.findAllByUser_IdAndRevokedAtIsNull(userId)
                .stream()
                .filter(sanction -> sanction.isPermanent() || sanction.getUntil().isAfter(now))
                .toList();
        // 영구 제재 중이면 이어 붙일 끝이 없으므로 지금부터 시작한다
        if (unfinished.stream().anyMatch(Sanction::isPermanent)) {
            return now;
        }
        return unfinished.stream()
                .map(Sanction::getUntil)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .filter(until -> until.isAfter(now))
                .orElse(now);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
