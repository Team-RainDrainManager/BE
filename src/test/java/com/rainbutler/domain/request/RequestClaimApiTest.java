package com.rainbutler.domain.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rainbutler.domain.request.service.SubstituteClaimService;
import com.rainbutler.domain.user.entity.Role;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.security.JwtTokenProvider;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 대신 점검 수락 API의 검증 순서 · 반경 · 하루 제한 · 선착순을 검증합니다.
 */
@AutoConfigureMockMvc
class RequestClaimApiTest extends RequestIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2027, 11, 1, 10, 0);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private SubstituteClaimService substituteClaimService;

    private Long forecast;
    private Long owner;
    private Long ownerDrain;
    private Long ownerAdoption;
    private Long helper;

    @BeforeEach
    void setUp() {
        now(NOW);
        LocalDate today = NOW.toLocalDate();
        forecast = fixtures.forecast(today, today.atTime(18, 0), false);
        owner = fixtures.user("원래집사");
        ownerDrain = fixtures.drain(RequestFixtures.BASE_LAT, RequestFixtures.BASE_LNG, false);
        ownerAdoption = fixtures.adoption(owner, ownerDrain, "또랑이");
        helper = fixtures.user("도움집사");
    }

    @Test
    void 공개된_요청을_맡으면_CLAIMED이고_원래_입양자에게_알린다() throws Exception {
        Long request = openRequest(ownerDrain, ownerAdoption);

        claim(helper, request, RequestFixtures.north(100))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestId").value(request))
                .andExpect(jsonPath("$.data.state").value("CLAIMED"))
                .andExpect(jsonPath("$.data.claimedAt").value("2027-11-01T10:00:00"))
                .andExpect(jsonPath("$.data.completeDeadlineAt").value("2027-11-01T17:00:00"));

        assertThat(fixtures.status(request)).isEqualTo("CLAIMED");
        assertThat(fixtures.lastNotification(owner, "REQUEST_CLAIMED"))
                .isEqualTo("대신 점검을 맡은 이웃이 생겼어요 | 도움집사님이 대신 살펴봐 주기로 했어요.");
    }

    @Test
    void 현재_위치가_300m를_넘으면_400이다() throws Exception {
        Long request = openRequest(ownerDrain, ownerAdoption);

        claim(helper, request, RequestFixtures.north(301))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SUBSTITUTE_OUT_OF_RANGE"));
        claim(helper, request, RequestFixtures.north(299)).andExpect(status().isOk());
    }

    @Test
    void 온보딩_제재_없는_요청_내_또랑이_순으로_막는다() throws Exception {
        Long request = openRequest(ownerDrain, ownerAdoption);

        jdbcTemplate.update("UPDATE users SET onboarding_completed_at = null WHERE id = ?", helper);
        claim(helper, request, RequestFixtures.BASE_LAT)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ONBOARDING_REQUIRED"));

        Long sanctioned = fixtures.user("제재집사");
        fixtures.sanction(sanctioned);
        claim(sanctioned, request, RequestFixtures.BASE_LAT)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("USER_SANCTIONED"));

        claim(owner, 999_999L, RequestFixtures.BASE_LAT)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("REQUEST_NOT_FOUND"));

        claim(owner, request, RequestFixtures.BASE_LAT)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SUBSTITUTE_OWN_REQUEST"));
    }

    @Test
    void 하루_3건까지_맡을_수_있고_취소_놓침도_센다() throws Exception {
        claimedEarlierToday("CANCELLED");
        claimedEarlierToday("MISSED");
        Long third = openRequest(ownerDrain, ownerAdoption);
        claim(helper, third, RequestFixtures.BASE_LAT).andExpect(status().isOk());

        Long other = fixtures.drain(RequestFixtures.north(10), RequestFixtures.BASE_LNG, true);
        Long fourth = openRequest(other, null);
        claim(helper, fourth, RequestFixtures.north(10))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SUBSTITUTE_DAILY_LIMIT"));
    }

    @Test
    void 어제_맡은_것은_오늘_횟수에_넣지_않는다() throws Exception {
        for (int i = 0; i < 3; i++) {
            Long drain = fixtures.drain(RequestFixtures.north(500 + i), RequestFixtures.BASE_LNG,
                    true);
            Long done = fixtures.request(forecast, drain, null, "COMPLETED", NOW, NOW.plusHours(1));
            jdbcTemplate.update("UPDATE inspection_request SET substitute_user_id = ?, "
                    + "claimed_at = ? WHERE id = ?", helper,
                    Timestamp.valueOf(NOW.minusDays(1)), done);
        }

        claim(helper, openRequest(ownerDrain, ownerAdoption), RequestFixtures.BASE_LAT)
                .andExpect(status().isOk());
    }

    @Test
    void 공개가_아니거나_마감이_지났거나_이미_맡은_요청은_409다() throws Exception {
        Long sent = fixtures.request(forecast, ownerDrain, ownerAdoption, "SENT",
                NOW.plusHours(1), NOW.withHour(17));
        claim(helper, sent, RequestFixtures.BASE_LAT)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REQUEST_NOT_OPEN"));

        Long lateDrain = fixtures.drain(RequestFixtures.north(20), RequestFixtures.BASE_LNG, true);
        Long late = fixtures.request(forecast, lateDrain, null, "OPEN", NOW.minusHours(2), NOW);
        claim(helper, late, RequestFixtures.north(20))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REQUEST_NOT_OPEN"));

        Long takenDrain = fixtures.drain(RequestFixtures.north(30), RequestFixtures.BASE_LNG, true);
        Long taken = openRequest(takenDrain, null);
        fixtures.claim(taken, fixtures.user("먼저집사"));
        claim(helper, taken, RequestFixtures.north(30))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REQUEST_ALREADY_CLAIMED"));
    }

    @Test
    void 동시에_맡으면_한_명만_성공한다() throws Exception {
        Long request = openRequest(ownerDrain, ownerAdoption);
        List<Long> users = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            users.add(fixtures.user("동시집사" + i));
        }

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(users.size());
        List<Future<ErrorCode>> results = new ArrayList<>();
        for (Long user : users) {
            Callable<ErrorCode> task = () -> {
                start.await();
                try {
                    substituteClaimService.claim(user, request, RequestFixtures.BASE_LAT,
                            RequestFixtures.BASE_LNG);
                    return null;
                } catch (BusinessException e) {
                    return e.getErrorCode();
                }
            };
            results.add(pool.submit(task));
        }
        start.countDown();
        List<ErrorCode> outcomes = new ArrayList<>();
        for (Future<ErrorCode> result : results) {
            outcomes.add(result.get());
        }
        pool.shutdown();

        assertThat(outcomes.stream().filter(code -> code == null).count()).isEqualTo(1);
        assertThat(outcomes.stream().filter(code -> code != null))
                .containsOnly(ErrorCode.REQUEST_ALREADY_CLAIMED);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM inspection_request "
                + "WHERE id = ? AND substitute_user_id IS NOT NULL", Integer.class, request))
                .isEqualTo(1);
    }

    private Long openRequest(Long drain, Long adoption) {
        return fixtures.request(forecast, drain, adoption, "OPEN", NOW.minusHours(1),
                NOW.withHour(17));
    }

    private void claimedEarlierToday(String status) {
        Long drain = fixtures.drain(RequestFixtures.north(1000), RequestFixtures.BASE_LNG, true);
        Long request = fixtures.request(forecast, drain, null, status, NOW, NOW.plusHours(1));
        jdbcTemplate.update("UPDATE inspection_request SET substitute_user_id = ?, claimed_at = ? "
                + "WHERE id = ?", helper, Timestamp.valueOf(NOW.withHour(0)), request);
    }

    private ResultActions claim(Long userId, Long requestId, double latitude) throws Exception {
        return mockMvc.perform(post("/api/v1/requests/" + requestId + "/claim")
                .header("Authorization", "Bearer "
                        + jwtTokenProvider.createAccessToken(userId, Role.USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"latitude\":" + latitude + ",\"longitude\":"
                        + RequestFixtures.BASE_LNG + "}"));
    }
}
