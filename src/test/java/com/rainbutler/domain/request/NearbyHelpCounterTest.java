package com.rainbutler.domain.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.rainbutler.domain.drain.repository.DrainRepository;
import com.rainbutler.domain.request.service.NearbyHelpCounter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 근처(100m) 도움 필요 개수 계산을 검증합니다.
 */
class NearbyHelpCounterTest extends RequestIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2028, 4, 1, 10, 0);

    @Autowired
    private NearbyHelpCounter counter;

    @Autowired
    private DrainRepository drainRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long forecast;
    private Long center;

    @BeforeEach
    void setUp() {
        now(NOW);
        LocalDate today = NOW.toLocalDate();
        forecast = fixtures.forecast(today, today.atTime(18, 0), false);
        center = fixtures.drain(RequestFixtures.BASE_LAT, RequestFixtures.BASE_LNG, false);
    }

    @Test
    void 반경_100m_안의_공개_요청_빗물받이만_센다() {
        // 정확히 100m는 부동소수 오차가 있어 반경 바로 안 · 밖으로 경계를 확인한다
        open(50);
        open(99.99);
        open(100.01);

        assertThat(count(center)).isEqualTo(2);
    }

    @Test
    void 자기_빗물받이_마감_지난_요청_맡은_요청_끝난_요청은_세지_않는다() {
        fixtures.request(forecast, center, null, "OPEN", NOW, NOW.withHour(17));
        Long late = fixtures.drain(RequestFixtures.north(10), RequestFixtures.BASE_LNG, false);
        fixtures.request(forecast, late, null, "OPEN", NOW.minusHours(2), NOW);
        Long claimedDrain = fixtures.drain(RequestFixtures.north(20), RequestFixtures.BASE_LNG,
                false);
        Long claimed = fixtures.request(forecast, claimedDrain, null, "OPEN", NOW,
                NOW.withHour(17));
        fixtures.claim(claimed, fixtures.user("대신집사"));
        Long doneDrain = fixtures.drain(RequestFixtures.north(30), RequestFixtures.BASE_LNG, false);
        fixtures.request(forecast, doneDrain, null, "COMPLETED", NOW, NOW.withHour(17));

        assertThat(count(center)).isZero();
    }

    @Test
    void 같은_빗물받이에_요청이_여러_개여도_1개로_센다() {
        Long drain = open(50);
        LocalDate tomorrow = NOW.toLocalDate().plusDays(1);
        Long other = fixtures.forecast(tomorrow, tomorrow.atTime(15, 0), true);
        fixtures.request(other, drain, null, "OPEN", NOW, NOW.withHour(17));

        assertThat(count(center)).isEqualTo(1);
    }

    @Test
    void 여러_기준을_한_번에_센다() {
        Long far = fixtures.drain(RequestFixtures.north(1000), RequestFixtures.BASE_LNG, false);
        open(50);
        open(1050);
        open(1060);

        Map<Long, Integer> counts = transactionTemplate.execute(status -> counter.countOpenNear(
                drainRepository.findAllById(List.of(center, far)), NOW));

        assertThat(counts).containsEntry(center, 1).containsEntry(far, 2);
    }

    private Long open(double metersNorth) {
        Long drain = fixtures.drain(RequestFixtures.north(metersNorth), RequestFixtures.BASE_LNG,
                false);
        fixtures.request(forecast, drain, null, "OPEN", NOW, NOW.withHour(17));
        return drain;
    }

    private int count(Long drainId) {
        return transactionTemplate.execute(status ->
                counter.countOpenNear(drainRepository.findById(drainId).orElseThrow(), NOW));
    }
}
