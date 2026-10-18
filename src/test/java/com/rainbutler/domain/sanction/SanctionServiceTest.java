package com.rainbutler.domain.sanction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rainbutler.domain.sanction.entity.Sanction;
import com.rainbutler.domain.sanction.repository.SanctionRepository;
import com.rainbutler.domain.sanction.service.SanctionService;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.security.JwtTokenProvider;
import com.rainbutler.support.IntegrationTestSupport;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 제재 부과 단계 · 이어서 시작 · 효력 판단 · 내 제재 내역 API를 검증합니다. (시각은 Clock으로 고정)
 */
@AutoConfigureMockMvc
class SanctionServiceTest extends IntegrationTestSupport {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 10, 9, 0);

    @Autowired
    private SanctionService sanctionService;

    @Autowired
    private SanctionRepository sanctionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Clock clock;

    private User user;

    @BeforeEach
    void setUp() {
        setNow(T0);
        user = saveUser();
    }

    @Test
    void 첫_번째는_7일_두_번째는_14일_세_번째부터_영구() {
        Sanction first = impose();
        assertThat(first.isPermanent()).isFalse();
        assertThat(first.getStartsAt()).isEqualTo(T0);
        assertThat(first.getUntil()).isEqualTo(T0.plusDays(7));

        // 첫 제재가 끝난 뒤 두 번째
        setNow(T0.plusDays(10));
        Sanction second = impose();
        assertThat(second.getStartsAt()).isEqualTo(T0.plusDays(10));
        assertThat(second.getUntil()).isEqualTo(T0.plusDays(24));

        setNow(T0.plusDays(30));
        Sanction third = impose();
        assertThat(third.isPermanent()).isTrue();
        assertThat(third.getUntil()).isNull();
    }

    @Test
    void 해제된_제재는_누적_횟수에서_빠진다() {
        Sanction first = impose();
        first.revoke(T0.plusDays(1));
        sanctionRepository.save(first);

        setNow(T0.plusDays(2));
        Sanction next = impose();

        // 해제된 1회는 세지 않으므로 다시 1회째(7일)
        assertThat(next.getUntil()).isEqualTo(T0.plusDays(2).plusDays(7));
    }

    @Test
    void 제재_중에_추가로_부과하면_기존_종료_시각부터_이어서_시작한다() {
        impose();                                   // T0 ~ T0+7
        setNow(T0.plusDays(1));
        Sanction second = impose();                 // 2회째 14일, T0+7부터

        assertThat(second.getStartsAt()).isEqualTo(T0.plusDays(7));
        assertThat(second.getUntil()).isEqualTo(T0.plusDays(21));
        // 두 번째는 아직 시작 전이지만 첫 번째로 제재 중이다
        assertThat(sanctionService.isSanctioned(user.getId())).isTrue();

        setNow(T0.plusDays(8));
        assertThat(sanctionService.findActive(user.getId())).get()
                .extracting(Sanction::getId).isEqualTo(second.getId());
    }

    @Test
    void 종료_시각_정각에는_제재가_끝난다() {
        impose();

        setNow(T0.plusDays(7).minusSeconds(1));
        assertThat(sanctionService.isSanctioned(user.getId())).isTrue();
        setNow(T0.plusDays(7));
        assertThat(sanctionService.isSanctioned(user.getId())).isFalse();
    }

    @Test
    void 해제된_제재는_효력이_없고_영구_제재는_계속_효력이_있다() {
        Sanction sanction = impose();
        sanction.revoke(T0.plusHours(1));
        sanctionRepository.save(sanction);
        setNow(T0.plusHours(2));
        assertThat(sanctionService.isSanctioned(user.getId())).isFalse();

        User permanentUser = saveUser();
        sanctionRepository.save(Sanction.permanent(permanentUser, null, null, "허위 제보", T0));
        setNow(T0.plusYears(10));
        assertThat(sanctionService.isSanctioned(permanentUser.getId())).isTrue();
    }

    @Test
    void 제재_중이면_가드가_403_USER_SANCTIONED를_던진다() {
        impose();

        assertThatThrownBy(() -> sanctionService.requireNotSanctioned(user.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_SANCTIONED);
        assertThat(ErrorCode.USER_SANCTIONED.getStatus().value()).isEqualTo(403);
    }

    @Test
    void 내_제재_내역은_최신순이고_본인_것만_보인다() throws Exception {
        Sanction first = impose();
        first.revoke(T0.plusDays(1));
        sanctionRepository.save(first);
        setNow(T0.plusDays(3));
        Sanction second = impose();

        User other = saveUser();
        sanctionRepository.save(Sanction.temporary(other, null, null, "다른 사용자",
                T0, T0.plusDays(7)));

        String token = jwtTokenProvider.createAccessToken(user.getId(), user.getRole());
        mockMvc.perform(get("/api/v1/users/me/sanctions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].sanctionId").value(second.getId()))
                .andExpect(jsonPath("$.data[0].active").value(true))
                .andExpect(jsonPath("$.data[0].permanent").value(false))
                .andExpect(jsonPath("$.data[0].startsAt").exists())
                .andExpect(jsonPath("$.data[0].revokedAt").doesNotExist())
                .andExpect(jsonPath("$.data[1].sanctionId").value(first.getId()))
                .andExpect(jsonPath("$.data[1].active").value(false))
                .andExpect(jsonPath("$.data[1].revokedAt").exists());
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/sanctions")).andExpect(status().isUnauthorized());
    }

    private Sanction impose() {
        return sanctionService.impose(user.getId(), null, "허위 제보", null);
    }

    private User saveUser() {
        return userRepository.save(User.local(
                UUID.randomUUID() + "@test.com", "{noop}irrelevant", "제재집사"));
    }

    private void setNow(LocalDateTime now) {
        given(clock.getZone()).willReturn(SEOUL);
        given(clock.instant()).willReturn(now.atZone(SEOUL).toInstant());
    }
}
