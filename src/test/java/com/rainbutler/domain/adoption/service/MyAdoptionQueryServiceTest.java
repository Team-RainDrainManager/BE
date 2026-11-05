package com.rainbutler.domain.adoption.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.rainbutler.domain.adoption.dto.Response.AdoptionDetailResponse;
import com.rainbutler.domain.adoption.dto.Response.MyAdoptionListResponse;
import com.rainbutler.domain.adoption.dto.Response.MyAdoptionResponse;
import com.rainbutler.domain.adoption.entity.Adoption;
import com.rainbutler.domain.adoption.entity.AdoptionStatus;
import com.rainbutler.domain.adoption.repository.AdoptionRepository;
import com.rainbutler.domain.drain.entity.Drain;
import com.rainbutler.domain.drain.entity.DrainDisplayStatus;
import com.rainbutler.domain.drain.service.DrainDisplayStatusPolicy;
import com.rainbutler.domain.drain.service.GrowthLevelPolicy;
import com.rainbutler.domain.report.repository.ReportRepository;
import com.rainbutler.domain.user.entity.Role;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.global.config.PolicyProperties;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.storage.StorageService;
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
    @Mock
    private ReportRepository reportRepository;
    @Mock
    private StorageService storageService;

    private MyAdoptionQueryService myAdoptionQueryService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.from(NOW.atZone(ZoneId.of("Asia/Seoul"))),
                ZoneId.of("Asia/Seoul"));
        PolicyProperties policies = TestPolicies.defaults();
        myAdoptionQueryService = new MyAdoptionQueryService(
                adoptionRepository, userRepository, reportRepository, policies,
                new GrowthLevelPolicy(), new DrainDisplayStatusPolicy(), storageService, clock);
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

    @Test
    void 입양_상세는_돌봄_정보와_빗물받이_정보를_내려준다() {
        User owner = mock(User.class);
        given(owner.getId()).willReturn(USER_ID);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(owner));
        Adoption adoption = detailAdoption(owner, 10L, AdoptionStatus.ACTIVE);
        given(adoptionRepository.findById(10L)).willReturn(Optional.of(adoption));
        given(storageService.toReadUrl("drain-photo")).willReturn("http://drain-photo");

        AdoptionDetailResponse detail = myAdoptionQueryService.findDetail(USER_ID, 10L);

        assertThat(detail.adoptionId()).isEqualTo(10L);
        assertThat(detail.nickname()).isEqualTo("또랑이");
        assertThat(detail.adoptedAt()).isEqualTo(NOW.minusDays(2));
        assertThat(detail.level()).isEqualTo(2);
        assertThat(detail.score()).isEqualTo(60);
        assertThat(detail.levelTitle()).isEqualTo("새싹");
        assertThat(detail.daysUntilNextManagement()).isEqualTo(12);
        assertThat(detail.releaseWarning()).isFalse();
        assertThat(detail.displayStatus()).isEqualTo(DrainDisplayStatus.NORMAL);
        assertThat(detail.drain().drainId()).isEqualTo(20L);
        assertThat(detail.drain().name()).isEqualTo("역곡역 앞");
        assertThat(detail.drain().address()).isEqualTo("경기도 부천시 역곡로");
        assertThat(detail.drain().photoUrl()).isEqualTo("http://drain-photo");
        assertThat(detail.drain().risk()).isTrue();
    }

    @Test
    void 다른_사람의_입양_상세는_볼_수_없다() {
        User requester = mock(User.class);
        given(requester.getId()).willReturn(USER_ID);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(requester));
        User owner = mock(User.class);
        given(owner.getId()).willReturn(2L);
        Adoption adoption = detailAdoption(owner, 10L, AdoptionStatus.ACTIVE);
        given(adoptionRepository.findById(10L)).willReturn(Optional.of(adoption));

        assertThatThrownBy(() -> myAdoptionQueryService.findDetail(USER_ID, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void 해제된_입양_상세는_찾을_수_없다() {
        User owner = mock(User.class);
        lenient().when(owner.getId()).thenReturn(USER_ID);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(owner));
        Adoption adoption = detailAdoption(owner, 10L, AdoptionStatus.RELEASED);
        given(adoptionRepository.findById(10L)).willReturn(Optional.of(adoption));

        assertThatThrownBy(() -> myAdoptionQueryService.findDetail(USER_ID, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ADOPTION_NOT_FOUND);
    }

    @Test
    void 관리자는_다른_사람의_입양_상세를_볼_수_있다() {
        User admin = mock(User.class);
        lenient().when(admin.getId()).thenReturn(USER_ID);
        given(admin.getRole()).willReturn(Role.ADMIN);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(admin));
        User owner = mock(User.class);
        lenient().when(owner.getId()).thenReturn(2L);
        Adoption adoption = detailAdoption(owner, 10L, AdoptionStatus.ACTIVE);
        given(adoptionRepository.findById(10L)).willReturn(Optional.of(adoption));

        assertThat(myAdoptionQueryService.findDetail(USER_ID, 10L).adoptionId())
                .isEqualTo(10L);
    }

    private Adoption detailAdoption(User owner, Long adoptionId, AdoptionStatus status) {
        Drain drain = mock(Drain.class);
        lenient().when(drain.getId()).thenReturn(20L);
        lenient().when(drain.getName()).thenReturn("역곡역 앞");
        lenient().when(drain.getAddress()).thenReturn("경기도 부천시 역곡로");
        lenient().when(drain.getLatitude()).thenReturn(37.5);
        lenient().when(drain.getLongitude()).thenReturn(127.0);
        lenient().when(drain.getPhotoUrl()).thenReturn("drain-photo");
        lenient().when(drain.getDirtLevel()).thenReturn(2);
        lenient().when(drain.isRisk()).thenReturn(true);

        Adoption adoption = mock(Adoption.class);
        lenient().when(adoption.getId()).thenReturn(adoptionId);
        lenient().when(adoption.getNickname()).thenReturn("또랑이");
        lenient().when(adoption.getUser()).thenReturn(owner);
        lenient().when(adoption.getDrain()).thenReturn(drain);
        lenient().when(adoption.getStatus()).thenReturn(status);
        lenient().when(adoption.getLevel()).thenReturn(2);
        lenient().when(adoption.getScore()).thenReturn(60);
        lenient().when(adoption.getAdoptedAt()).thenReturn(NOW.minusDays(2));
        lenient().when(adoption.getLastManagedAt()).thenReturn(NOW.minusDays(2));
        lenient().when(adoption.getNextManagementAt()).thenReturn(NOW.plusDays(12));
        lenient().when(adoption.getIgnoreCount()).thenReturn(1);
        return adoption;
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
