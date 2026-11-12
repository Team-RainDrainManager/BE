package com.rainbutler.domain.leaderboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import com.rainbutler.global.storage.StorageService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 리더보드의 점수 정렬 · 동점 순위 · 내 순위 · 기간 · 입력 검증을 확인합니다.
 */
@ExtendWith(MockitoExtension.class)
class LeaderboardQueryServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 4, 12, 0);
    private static final LocalDateTime MONDAY = LocalDateTime.of(2026, 11, 2, 0, 0);
    private static final Clock CLOCK =
            Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);

    private static final Long ME = 1L;

    @Mock
    private LeaderboardRepository leaderboardRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private StorageService storageService;

    private LeaderboardQueryService service;
    private User me;

    @BeforeEach
    void setUp() {
        service = new LeaderboardQueryService(leaderboardRepository, userRepository,
                storageService, CLOCK);
        lenient().when(storageService.toReadUrl(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        me = User.local(UUID.randomUUID() + "@test.com", "{noop}x", "나");
        ReflectionTestUtils.setField(me, "id", ME);
        ReflectionTestUtils.setField(me, "profileImage", "https://image/me.png");
        lenient().when(userRepository.findById(ME)).thenReturn(Optional.of(me));
    }

    @Test
    void 주간_순위를_점수순으로_내려주고_동점은_같은_순위다() {
        given(leaderboardRepository.findWeeklyScores(MONDAY, NOW))
                .willReturn(List.of(entry(ME, "나", 10L), entry(2L, "둘", 10L), entry(3L, "셋", 5L)));

        LeaderboardResponse response = service.findRankings(ME, null, null);

        assertThat(response.type()).isEqualTo(LeaderboardType.WEEKLY);
        assertThat(response.periodStart()).isEqualTo(MONDAY);
        assertThat(response.totalParticipants()).isEqualTo(3);
        assertThat(response.rankings())
                .extracting(LeaderboardRowResponse::nickname)
                .containsExactly("나", "둘", "셋");
        assertThat(response.rankings())
                .extracting(LeaderboardRowResponse::rank)
                .containsExactly(1, 1, 3);
        assertThat(response.rankings())
                .extracting(LeaderboardRowResponse::isMe)
                .containsExactly(true, false, false);
        assertThat(response.me().rank()).isEqualTo(1);
        assertThat(response.me().score()).isEqualTo(10L);
    }

    @Test
    void 상위_limit_밖이어도_me에_내_순위가_내려간다() {
        given(leaderboardRepository.findWeeklyScores(any(), any()))
                .willReturn(List.of(entry(2L, "둘", 20L), entry(3L, "셋", 15L),
                        entry(ME, "나", 5L)));

        LeaderboardResponse response = service.findRankings(ME, "WEEKLY", 2);

        assertThat(response.rankings()).extracting(LeaderboardRowResponse::nickname)
                .containsExactly("둘", "셋");
        assertThat(response.me().rank()).isEqualTo(3);
        assertThat(response.me().score()).isEqualTo(5L);
    }

    @Test
    void 점수가_0이면_순위에서_빠지고_me_rank는_null이다() {
        given(leaderboardRepository.findWeeklyScores(any(), any()))
                .willReturn(List.of(entry(2L, "둘", 10L)));

        LeaderboardResponse response = service.findRankings(ME, "WEEKLY", 20);

        assertThat(response.rankings()).hasSize(1);
        assertThat(response.me().rank()).isNull();
        assertThat(response.me().score()).isZero();
        assertThat(response.me().nickname()).isEqualTo("나");
        assertThat(response.me().isMe()).isTrue();
    }

    @Test
    void 내_점수가_음수여도_순위는_빠지고_점수는_그대로_내려간다() {
        given(leaderboardRepository.findWeeklyScores(any(), any()))
                .willReturn(List.of(entry(2L, "둘", 10L), entry(ME, "나", -2L)));

        LeaderboardResponse response = service.findRankings(ME, "WEEKLY", 20);

        assertThat(response.rankings()).hasSize(1);
        assertThat(response.me().rank()).isNull();
        assertThat(response.me().score()).isEqualTo(-2L);
    }

    @Test
    void 프로필_사진_key는_조회_URL로_바꿔_내려준다() {
        given(storageService.toReadUrl("test/profile/2/uuid.jpg"))
                .willReturn("https://s3/read/test/profile/2/uuid.jpg");
        given(storageService.toReadUrl("https://image/me.png"))
                .willReturn("https://image/me.png");
        given(leaderboardRepository.findWeeklyScores(any(), any()))
                .willReturn(List.of(
                        new LeaderboardEntry(2L, "둘", "test/profile/2/uuid.jpg", 20L),
                        entry(ME, "나", 5L)));

        LeaderboardResponse response = service.findRankings(ME, "WEEKLY", 20);

        assertThat(response.rankings().get(0).profileImage())
                .isEqualTo("https://s3/read/test/profile/2/uuid.jpg");
        assertThat(response.me().profileImage()).isEqualTo("https://image/me.png");
    }

    @Test
    void TOTAL은_전체_집계를_쓰고_periodStart가_null이다() {
        given(leaderboardRepository.findTotalScores())
                .willReturn(List.of(entry(ME, "나", 100L)));

        LeaderboardResponse response = service.findRankings(ME, "total", 20);

        assertThat(response.type()).isEqualTo(LeaderboardType.TOTAL);
        assertThat(response.periodStart()).isNull();
        assertThat(response.me().rank()).isEqualTo(1);
        assertThat(response.me().score()).isEqualTo(100L);
        verify(leaderboardRepository).findTotalScores();
        verify(leaderboardRepository, never()).findWeeklyScores(any(), any());
    }

    @Test
    void limit은_최대_100으로_줄인다() {
        List<LeaderboardEntry> entries = new ArrayList<>();
        for (long i = 1; i <= 150; i++) {
            entries.add(entry(i, "사용자" + i, 1_000L - i));
        }
        given(leaderboardRepository.findWeeklyScores(any(), any())).willReturn(entries);

        LeaderboardResponse response = service.findRankings(ME, "WEEKLY", 500);

        assertThat(response.rankings()).hasSize(100);
        assertThat(response.rankings().get(0).score()).isEqualTo(999L);
        assertThat(response.rankings().get(99).score()).isEqualTo(900L);
    }

    @Test
    void 내_순위만_조회한다() {
        given(leaderboardRepository.findWeeklyScores(MONDAY, NOW))
                .willReturn(List.of(entry(2L, "둘", 20L), entry(ME, "나", 5L)));

        MyRankResponse response = service.findMyRank(ME, "WEEKLY");

        assertThat(response.type()).isEqualTo(LeaderboardType.WEEKLY);
        assertThat(response.periodStart()).isEqualTo(MONDAY);
        assertThat(response.totalParticipants()).isEqualTo(2);
        assertThat(response.rank()).isEqualTo(2);
        assertThat(response.score()).isEqualTo(5L);
    }

    @Test
    void 잘못된_type은_INVALID_INPUT() {
        assertThatThrownBy(() -> service.findRankings(ME, "ZONE", 20))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
        verifyNoInteractions(leaderboardRepository);
    }

    @Test
    void limit이_1보다_작으면_INVALID_INPUT() {
        assertThatThrownBy(() -> service.findRankings(ME, "WEEKLY", 0))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
        verifyNoInteractions(leaderboardRepository);
    }

    @Test
    void 사용자가_없으면_USER_NOT_FOUND() {
        when(userRepository.findById(ME)).thenReturn(Optional.empty());
        given(leaderboardRepository.findWeeklyScores(any(), any())).willReturn(List.of());

        assertThatThrownBy(() -> service.findRankings(ME, "WEEKLY", 20))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    private LeaderboardEntry entry(Long userId, String nickname, Long score) {
        return new LeaderboardEntry(userId, nickname, null, score);
    }
}
