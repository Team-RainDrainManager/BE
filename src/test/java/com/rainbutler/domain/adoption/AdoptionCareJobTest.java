package com.rainbutler.domain.adoption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.rainbutler.domain.adoption.job.AdoptionCareJob;
import com.rainbutler.domain.request.RequestFixtures;
import com.rainbutler.support.IntegrationTestSupport;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 미관리 경고 · 자동 파양 배치를 검증합니다.
 */
class AdoptionCareJobTest extends IntegrationTestSupport {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2027, 8, 1, 9, 0);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AdoptionCareJob job;

    @MockitoBean
    private Clock clock;

    private RequestFixtures fixtures;
    private Long forecast;

    @BeforeEach
    void setUp() {
        given(clock.getZone()).willReturn(SEOUL);
        given(clock.instant()).willReturn(NOW.atZone(SEOUL).toInstant());
        fixtures = new RequestFixtures(jdbcTemplate);
        fixtures.neutralize();
        forecast = fixtures.forecast(NOW.toLocalDate().plusDays(1),
                NOW.plusDays(1).withHour(15), false);
    }

    @Test
    void 관리_기준에서_35일이_지나면_자동_파양하고_요청을_재공개한다() {
        Long owner = fixtures.user("집사");
        Long drain = fixtures.drain(RequestFixtures.BASE_LAT, RequestFixtures.BASE_LNG, false);
        Long adoption = fixtures.adoption(owner, drain, "또랑이");
        managedDaysAgo(adoption, 35);
        Long request = fixtures.request(forecast, drain, adoption, "SENT", NOW.plusHours(6),
                NOW.plusHours(14));

        job.run();

        assertThat(jdbcTemplate.queryForObject("SELECT status || '/' || release_reason "
                + "FROM adoption WHERE id = ?", String.class, adoption)).isEqualTo("RELEASED/AUTO");
        assertThat(jdbcTemplate.queryForObject("SELECT released_at FROM adoption WHERE id = ?",
                LocalDateTime.class, adoption)).isEqualTo(NOW);
        assertThat(jdbcTemplate.queryForObject("SELECT adoption_status FROM drain WHERE id = ?",
                String.class, drain)).isEqualTo("AVAILABLE");

        assertThat(fixtures.status(request)).isEqualTo("OPEN");
        assertThat(jdbcTemplate.queryForObject("SELECT adoption_id FROM inspection_request "
                + "WHERE id = ?", Long.class, request)).isNull();

        assertThat(fixtures.notificationCount(owner, "ADOPTION_RELEASED")).isEqualTo(1);
        assertThat(fixtures.notificationCount(owner, "ADOPTION_WARNING")).isZero();
    }

    @Test
    void 관리_기준에서_28일이_지나면_경고를_한_번만_보낸다() {
        Long owner = fixtures.user("집사");
        Long drain = fixtures.drain(RequestFixtures.BASE_LAT, RequestFixtures.BASE_LNG, false);
        Long adoption = fixtures.adoption(owner, drain, "또랑이");
        managedDaysAgo(adoption, 28);

        job.run();
        job.run();

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM adoption WHERE id = ?",
                String.class, adoption)).isEqualTo("ACTIVE");
        assertThat(jdbcTemplate.queryForObject("SELECT warned_at FROM adoption WHERE id = ?",
                LocalDateTime.class, adoption)).isEqualTo(NOW);
        assertThat(fixtures.notificationCount(owner, "ADOPTION_WARNING")).isEqualTo(1);
        assertThat(fixtures.notificationCount(owner, "ADOPTION_RELEASED")).isZero();
    }

    @Test
    void 아직_경고일이_되지_않았으면_아무것도_하지_않는다() {
        Long owner = fixtures.user("집사");
        Long drain = fixtures.drain(RequestFixtures.BASE_LAT, RequestFixtures.BASE_LNG, false);
        Long adoption = fixtures.adoption(owner, drain, "또랑이");
        managedDaysAgo(adoption, 27);

        job.run();

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM adoption WHERE id = ?",
                String.class, adoption)).isEqualTo("ACTIVE");
        assertThat(fixtures.notificationCount(owner, "ADOPTION_WARNING")).isZero();
        assertThat(fixtures.notificationCount(owner, "ADOPTION_RELEASED")).isZero();
    }

    @Test
    void 마지막_점검이_최근이면_경고하지_않는다() {
        Long owner = fixtures.user("집사");
        Long drain = fixtures.drain(RequestFixtures.BASE_LAT, RequestFixtures.BASE_LNG, false);
        Long adoption = fixtures.adoption(owner, drain, "또랑이");
        jdbcTemplate.update("UPDATE adoption SET adopted_at = ?, last_managed_at = ? WHERE id = ?",
                NOW.minusDays(40), NOW.minusDays(10), adoption);

        job.run();

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM adoption WHERE id = ?",
                String.class, adoption)).isEqualTo("ACTIVE");
        assertThat(fixtures.notificationCount(owner, "ADOPTION_WARNING")).isZero();
    }

    @Test
    void 점검_쉬기_중이면_건너뛴다() {
        Long owner = fixtures.user("집사");
        Long drain = fixtures.drain(RequestFixtures.BASE_LAT, RequestFixtures.BASE_LNG, false);
        Long adoption = fixtures.adoption(owner, drain, "또랑이");
        managedDaysAgo(adoption, 40);
        jdbcTemplate.update("UPDATE users SET pause_until = ? WHERE id = ?",
                NOW.plusDays(7), owner);

        job.run();

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM adoption WHERE id = ?",
                String.class, adoption)).isEqualTo("ACTIVE");
        assertThat(fixtures.notificationCount(owner, "ADOPTION_WARNING")).isZero();
        assertThat(fixtures.notificationCount(owner, "ADOPTION_RELEASED")).isZero();
    }

    private void managedDaysAgo(Long adoption, int days) {
        jdbcTemplate.update("UPDATE adoption SET adopted_at = ?, last_managed_at = ? WHERE id = ?",
                NOW.minusDays(days), NOW.minusDays(days), adoption);
    }
}
