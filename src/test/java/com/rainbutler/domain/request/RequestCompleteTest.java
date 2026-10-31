package com.rainbutler.domain.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rainbutler.domain.request.service.InspectionRequestService;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 점검 제출에서 부를 요청 완료 처리를 검증합니다.
 */
class RequestCompleteTest extends RequestIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2027, 11, 2, 10, 0);
    private static final LocalDateTime DEADLINE = NOW.withHour(17);

    @Autowired
    private InspectionRequestService inspectionRequestService;

    private Long forecast;
    private Long owner;
    private Long drain;
    private Long adoption;

    @BeforeEach
    void setUp() {
        now(NOW);
        LocalDate today = NOW.toLocalDate();
        forecast = fixtures.forecast(today, today.atTime(18, 0), false);
        owner = fixtures.user("집사");
        drain = fixtures.drain(RequestFixtures.BASE_LAT, RequestFixtures.BASE_LNG, false);
        adoption = fixtures.adoption(owner, drain, "또랑이");
    }

    @Test
    void 입양자는_응답_전이어도_수락_후에도_완료할_수_있다() {
        Long sent = fixtures.request(forecast, drain, adoption, "SENT", NOW.plusHours(1),
                DEADLINE);
        assertThat(inspectionRequestService.canComplete(sent, owner, NOW)).isTrue();
        inspectionRequestService.complete(sent, owner, NOW);
        assertThat(fixtures.status(sent)).isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject("SELECT completed_at FROM inspection_request "
                + "WHERE id = ?", LocalDateTime.class, sent)).isEqualTo(NOW);

        Long otherDrain = fixtures.drain(RequestFixtures.north(10), RequestFixtures.BASE_LNG,
                false);
        Long otherAdoption = fixtures.adoption(owner, otherDrain, "둘째");
        Long accepted = fixtures.request(forecast, otherDrain, otherAdoption, "ACCEPTED",
                NOW.plusHours(1), DEADLINE);
        inspectionRequestService.complete(accepted, owner, NOW);
        assertThat(fixtures.status(accepted)).isEqualTo("COMPLETED");
    }

    @Test
    void 대신_점검_수락자는_본인이_맡은_요청을_완료할_수_있다() {
        Long substitute = fixtures.user("대신집사");
        Long request = fixtures.request(forecast, drain, adoption, "OPEN", NOW, DEADLINE);
        fixtures.claim(request, substitute);

        assertThat(inspectionRequestService.canComplete(request, owner, NOW)).isFalse();
        assertThat(inspectionRequestService.canComplete(request, substitute, NOW)).isTrue();
        inspectionRequestService.complete(request, substitute, NOW);
        assertThat(fixtures.status(request)).isEqualTo("COMPLETED");
    }

    @Test
    void 남의_요청_공개된_요청_마감_지남_이미_끝난_요청은_완료할_수_없다() {
        Long stranger = fixtures.user("남");
        Long sent = fixtures.request(forecast, drain, adoption, "SENT", NOW.plusHours(1),
                DEADLINE);

        assertNotCompletable(sent, stranger, NOW);
        assertNotCompletable(sent, owner, DEADLINE);

        jdbcTemplate.update("UPDATE inspection_request SET status = 'OPEN' WHERE id = ?", sent);
        assertNotCompletable(sent, owner, NOW);

        jdbcTemplate.update("UPDATE inspection_request SET status = 'COMPLETED' WHERE id = ?",
                sent);
        assertNotCompletable(sent, owner, NOW);
        assertThat(inspectionRequestService.canComplete(999_999L, owner, NOW)).isFalse();
    }

    private void assertNotCompletable(Long request, Long user, LocalDateTime at) {
        assertThat(inspectionRequestService.canComplete(request, user, at)).isFalse();
        assertThatThrownBy(() -> inspectionRequestService.complete(request, user, at))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REQUEST_NOT_COMPLETABLE);
    }
}
