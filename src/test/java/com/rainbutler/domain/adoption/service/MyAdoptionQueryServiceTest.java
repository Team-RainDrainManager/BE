package com.rainbutler.domain.adoption.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.rainbutler.domain.adoption.dto.Response.MyAdoptionListResponse;
import com.rainbutler.domain.adoption.dto.Response.MyAdoptionResponse;
import com.rainbutler.domain.adoption.entity.Adoption;
import com.rainbutler.domain.adoption.entity.AdoptionStatus;
import com.rainbutler.domain.adoption.repository.AdoptionRepository;
import com.rainbutler.domain.drain.entity.Drain;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.global.config.PolicyProperties;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.support.TestPolicies;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 내 입양 목록의 돌봄 기준 시각·경고·외로움 상태를 검증합니다. */
@ExtendWith(MockitoExtension.class)
class MyAdoptionQueryServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 31, 9, 0);

    @Mock
    private AdoptionRepository adoptionRepository;
    @Mock
    private UserRepository userRepository;

    private MyAdoptionQueryService myAdoptionQueryService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.from(NOW.atZone(ZoneId.of("Asia/Seoul"))),
                ZoneId.of("Asia/Seoul"));
        PolicyProperties policies = TestPolicies.defaults();
        myAdoptionQueryService = new MyAdoptionQueryService(
                adoptionRepository, userRepository, policies, clock);
    }

    @Test
    void ACTIVE_입양의_마지막_돌봄과_경고_외로움_상태를_조회한다() {
        User user = mock(User.class);
        given(user.getId()).willReturn(USER_ID);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

        Adoption adoption = adoption(10L, 20L, NOW.minusDays(30), NOW.minusDays(29), 3);
        given(adoptionRepository.findAllByUser_IdAndStatusOrderByAdoptedAtDesc(
                USER_ID, AdoptionStatus.ACTIVE)).willReturn(List.of(adoption));

        MyAdoptionListResponse result = myAdoptionQueryService.findMine(USER_ID);

        assertThat(result.count()).isEqualTo(1);
        assertThat(result.limit()).isEqualTo(3);
        MyAdoptionResponse response = result.adoptions().get(0);
        assertThat(response.adoptionId()).isEqualTo(10L);
        assertThat(response.drainId()).isEqualTo(20L);
        assertThat(response.lastManagedAt()).isEqualTo(NOW.minusDays(29));
        assertThat(response.releaseWarning()).isTrue();
        assertThat(response.missedForecastCount()).isEqualTo(3);
        assertThat(response.lonely()).isTrue();
    }

    @Test
    void 마지막_승인_점검이_입양일보다_이르면_입양일을_마지막_돌봄으로_쓴다() {
        User user = mock(User.class);
        given(user.getId()).willReturn(USER_ID);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        Adoption adoption = adoption(10L, 20L, NOW.minusDays(10), NOW.minusDays(11), 2);
        given(adoptionRepository.findAllByUser_IdAndStatusOrderByAdoptedAtDesc(
                USER_ID, AdoptionStatus.ACTIVE)).willReturn(List.of(adoption));

        MyAdoptionResponse response = myAdoptionQueryService.findMine(USER_ID)
                .adoptions().get(0);

        assertThat(response.lastManagedAt()).isEqualTo(NOW.minusDays(10));
        assertThat(response.releaseWarning()).isFalse();
        assertThat(response.lonely()).isFalse();
    }

    @Test
    void 존재하지_않는_사용자는_조회할_수_없다() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> myAdoptionQueryService.findMine(USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("사용자를 찾을 수 없어요");
    }

    private Adoption adoption(Long adoptionId, Long drainId, LocalDateTime adoptedAt,
            LocalDateTime lastManagedAt, int ignoreCount) {
        Adoption adoption = mock(Adoption.class);
        Drain drain = mock(Drain.class);
        given(adoption.getId()).willReturn(adoptionId);
        given(adoption.getNickname()).willReturn("또랑이");
        given(adoption.getDrain()).willReturn(drain);
        given(drain.getId()).willReturn(drainId);
        given(adoption.getLevel()).willReturn(4);
        given(adoption.getScore()).willReturn(300);
        given(adoption.getAdoptedAt()).willReturn(adoptedAt);
        given(adoption.getLastManagedAt()).willReturn(lastManagedAt);
        given(adoption.getNextManagementAt()).willReturn(adoptedAt.plusDays(14));
        given(adoption.getIgnoreCount()).willReturn(ignoreCount);
        return adoption;
    }
}
