package com.rainbutler.domain.request;

import static org.mockito.BDDMockito.given;

import com.rainbutler.support.IntegrationTestSupport;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 비 예보 요청 통합 테스트 공통 설정입니다. 시각은 Clock으로 고정하고, 시작 전에 다른 테스트의 진행 중 데이터를
 * 비활성으로 돌립니다.
 */
abstract class RequestIntegrationTest extends IntegrationTestSupport {

    protected static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @MockitoBean
    protected Clock clock;

    protected RequestFixtures fixtures;

    @BeforeEach
    void setUpRequestIntegration() {
        fixtures = new RequestFixtures(jdbcTemplate);
        fixtures.neutralize();
    }

    /**
     * 현재 시각을 정합니다.
     */
    protected void now(LocalDateTime now) {
        given(clock.getZone()).willReturn(SEOUL);
        given(clock.instant()).willReturn(now.atZone(SEOUL).toInstant());
    }

    protected static LocalDateTime at(String value) {
        return LocalDateTime.parse(value);
    }
}
