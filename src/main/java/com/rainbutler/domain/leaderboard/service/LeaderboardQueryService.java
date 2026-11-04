package com.rainbutler.domain.leaderboard.service;

import com.rainbutler.domain.leaderboard.dto.LeaderboardResponse;
import com.rainbutler.domain.leaderboard.dto.LeaderboardRowResponse;
import com.rainbutler.domain.leaderboard.dto.MyRankResponse;
import com.rainbutler.domain.leaderboard.entity.LeaderboardType;
import com.rainbutler.domain.leaderboard.repository.LeaderboardEntry;
import com.rainbutler.domain.leaderboard.repository.LeaderboardRepository;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리더보드(주간 · 누적 참여 점수 순위)를 조회합니다.
 *
 * <p>점수는 {@code point_history}의 적립 합에서 회수 합을 뺀 값입니다. 점수가 0 이하인 사용자와
 * 관리자 · 시청 관계자는 순위에서 뺍니다. 동점이면 같은 순위를 주고 다음 순위를 건너뜁니다.
 * (예: 1, 1, 3)
 *
 * <p>주간은 월요일 00:00부터 현재까지입니다. 구역 · 단체 순위는 구역 · 단체 테이블이 정해지기
 * 전까지 지원하지 않습니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeaderboardQueryService {

    /** 한 번에 내려주는 기본 순위 개수 */
    public static final int DEFAULT_LIMIT = 20;
    /** 한 번에 내려주는 최대 순위 개수 (넘으면 이 값으로 줄인다) */
    public static final int MAX_LIMIT = 100;

    private final LeaderboardRepository leaderboardRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    /**
     * 리더보드를 조회합니다.
     *
     * @param userId 로그인 사용자 id
     * @param type 집계 기간 (null · 비어 있으면 WEEKLY)
     * @param limit 상위 순위 개수 (null이면 20, 100 초과면 100)
     * @return 상위 순위 · 내 순위
     * @throws BusinessException type · limit이 잘못된 경우 (INVALID_INPUT), 사용자가 없는 경우
     *     (USER_NOT_FOUND)
     */
    public LeaderboardResponse findRankings(Long userId, String type, Integer limit) {
        int pageSize = limit == null ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        if (pageSize < 1) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "limit은 1 이상이어야 해요.");
        }

        LeaderboardType period = parseType(type);
        Rankings rankings = rankings(period);
        User me = findUser(userId);

        List<LeaderboardRowResponse> rows = rankings.entries().stream()
                .limit(pageSize)
                .map(ranked -> row(ranked, ranked.entry().userId().equals(userId)))
                .toList();
        RankedEntry myRank = rankings.find(userId);
        LeaderboardRowResponse meRow = new LeaderboardRowResponse(
                myRank == null ? null : myRank.rank(),
                me.getId(),
                me.getNickname(),
                me.getProfileImage(),
                rankings.scoreOf(userId),
                true);

        return new LeaderboardResponse(
                period, periodStart(period), rankings.totalParticipants(), rows, meRow);
    }

    /**
     * 내 리더보드 순위만 조회합니다.
     *
     * @param userId 로그인 사용자 id
     * @param type 집계 기간 (null · 비어 있으면 WEEKLY)
     * @return 내 순위 · 점수
     * @throws BusinessException type이 잘못된 경우 (INVALID_INPUT), 사용자가 없는 경우
     *     (USER_NOT_FOUND)
     */
    public MyRankResponse findMyRank(Long userId, String type) {
        LeaderboardType period = parseType(type);
        Rankings rankings = rankings(period);
        findUser(userId);

        RankedEntry myRank = rankings.find(userId);
        return new MyRankResponse(
                period,
                periodStart(period),
                rankings.totalParticipants(),
                myRank == null ? null : myRank.rank(),
                rankings.scoreOf(userId));
    }

    private Rankings rankings(LeaderboardType type) {
        List<LeaderboardEntry> scores = type == LeaderboardType.WEEKLY
                ? leaderboardRepository.findWeeklyScores(
                        periodStart(type), LocalDateTime.now(clock))
                : leaderboardRepository.findTotalScores();
        return rank(scores);
    }

    /**
     * 점수 내림차순으로 정렬하고 동점은 같은 순위로 묶습니다.
     *
     * <p>순위에는 점수가 0보다 큰 사용자만 넣지만, 내 점수는 0 이하도 그대로 보여 줄 수 있도록
     * 모든 집계 결과를 함께 보관합니다.
     */
    private Rankings rank(List<LeaderboardEntry> scores) {
        Map<Long, Long> scoreByUser = scores.stream()
                .filter(entry -> entry.userId() != null && entry.score() != null)
                .collect(Collectors.toMap(
                        LeaderboardEntry::userId, LeaderboardEntry::score, (first, second) -> first));

        List<LeaderboardEntry> sorted = scores.stream()
                .filter(entry -> entry.score() != null && entry.score() > 0)
                .sorted(Comparator.comparing(LeaderboardEntry::score).reversed()
                        .thenComparing(LeaderboardEntry::userId))
                .toList();

        List<RankedEntry> ranked = new ArrayList<>(sorted.size());
        long previousScore = Long.MIN_VALUE;
        int rank = 0;
        for (int i = 0; i < sorted.size(); i++) {
            LeaderboardEntry entry = sorted.get(i);
            if (entry.score() != previousScore) {
                rank = i + 1;
                previousScore = entry.score();
            }
            ranked.add(new RankedEntry(entry, rank));
        }
        return new Rankings(List.copyOf(ranked), ranked.size(), Map.copyOf(scoreByUser));
    }

    private LeaderboardRowResponse row(RankedEntry ranked, boolean isMe) {
        LeaderboardEntry entry = ranked.entry();
        return new LeaderboardRowResponse(
                ranked.rank(),
                entry.userId(),
                entry.nickname(),
                entry.profileImage(),
                entry.score(),
                isMe);
    }

    private LeaderboardType parseType(String type) {
        if (type == null || type.isBlank()) {
            return LeaderboardType.WEEKLY;
        }
        try {
            return LeaderboardType.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "type은 WEEKLY · TOTAL 중 하나여야 해요. (ZONE · ORG 순위는 준비 중입니다.)");
        }
    }

    private LocalDateTime periodStart(LeaderboardType type) {
        if (type == LeaderboardType.TOTAL) {
            return null;
        }
        return LocalDate.now(clock)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay();
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * 정렬된 순위 목록 · 전체 참여자 수 · 사용자별 점수입니다.
     */
    private record Rankings(
            List<RankedEntry> entries,
            int totalParticipants,
            Map<Long, Long> scoreByUser) {

        RankedEntry find(Long userId) {
            return entries.stream()
                    .filter(ranked -> ranked.entry().userId().equals(userId))
                    .findFirst()
                    .orElse(null);
        }

        long scoreOf(Long userId) {
            return scoreByUser.getOrDefault(userId, 0L);
        }
    }

    /**
     * 순위가 매겨진 한 줄입니다.
     */
    private record RankedEntry(LeaderboardEntry entry, int rank) {
    }
}
