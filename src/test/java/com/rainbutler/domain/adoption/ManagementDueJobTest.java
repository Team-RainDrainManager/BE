package com.rainbutler.domain.adoption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.rainbutler.domain.adoption.job.ManagementDueJob;
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
 * 관리 예정일 하루 전 알림 배치를 검증합니다.
 */
class ManagementDueJobTest extends IntegrationTestSupport {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2027, 8, 1, 9, 0);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ManagementDueJob job;

    @MockitoBean
    private Clock clock;

    private RequestFixtures fixtures;

    @BeforeEach
    void setUp() {
        given(clock.getZone()).willReturn(SEOUL);
        given(clock.instant()).willReturn(NOW.atZone(SEOUL).toInstant());
        fixtures = new RequestFixtures(jdbcTemplate);
        fixtures.neutralize();
    }

    @Test
    void 관리_예정일이_내일이면_한_번_알린다() {
        Long owner = fixtures.user("집사");
        Long drain = fixtures.drain(RequestFixtures.BASE_LAT, RequestFixtures.BASE_LNG, false);
        Long adoption = fixtures.adoption(owner, drain, "또랑이");
        jdbcTemplate.update("UPDATE adoption SET next_management_at = ? WHERE id = ?",
                NOW.plusDays(1), adoption);

        job.run();
        job.run();

        assertThat(fixtures.notificationCount(owner, "MANAGEMENT_DUE")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT management_notified_at FROM adoption WHERE id = ?",
                LocalDateTime.class, adoption)).isEqualTo(NOW);
    }

    @Test
    void 관리_예정일이_내일이_아니면_알리지_않는다() {
        Long owner = fixtures.user("집사");
        Long drain = fixtures.drain(RequestFixtures.BASE_LAT, RequestFixtures.BASE_LNG, false);
        Long adoption = fixtures.adoption(owner, drain, "또랑이");
        jdbcTemplate.update("UPDATE adoption SET next_management_at = ? WHERE id = ?",
                NOW.plusDays(2), adoption);

        job.run();

        assertThat(fixtures.notificationCount(owner, "MANAGEMENT_DUE")).isZero();
    }

    @Test
    void 점검_쉬기_중이면_알리지_않는다() {
        Long owner = fixtures.user("집사");
        Long drain = fixtures.drain(RequestFixtures.BASE_LAT, RequestFixtures.BASE_LNG, false);
        Long adoption = fixtures.adoption(owner, drain, "또랑이");
        jdbcTemplate.update("UPDATE adoption SET next_management_at = ? WHERE id = ?",
                NOW.plusDays(1), adoption);
        jdbcTemplate.update("UPDATE users SET pause_until = ? WHERE id = ?",
                NOW.plusDays(7), owner);

        job.run();

        assertThat(fixtures.notificationCount(owner, "MANAGEMENT_DUE")).isZero();
    }

    @Test
    void 해제된_입양은_알리지_않는다() {
        Long owner = fixtures.user("집사");
        Long drain = fixtures.drain(RequestFixtures.BASE_LAT, RequestFixtures.BASE_LNG, false);
        Long adoption = fixtures.adoption(owner, drain, "또랑이");
        jdbcTemplate.update("UPDATE adoption SET status = 'RELEASED', "
                + "next_management_at = ? WHERE id = ?", NOW.plusDays(1), adoption);

        job.run();

        assertThat(fixtures.notificationCount(owner, "MANAGEMENT_DUE")).isZero();
    }
}
