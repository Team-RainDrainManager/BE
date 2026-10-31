package com.rainbutler.domain.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.rainbutler.domain.request.repository.InspectionRequestRepository;
import com.rainbutler.domain.request.service.SubstituteNotifier;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 대신 점검 근처 입양자 알림의 반경 · 제외 대상 · 묶음을 검증합니다.
 */
class SubstituteNotifierTest extends RequestIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2027, 6, 1, 10, 0);

    @Autowired
    private SubstituteNotifier substituteNotifier;

    @Autowired
    private InspectionRequestRepository inspectionRequestRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long forecast;

    @BeforeEach
    void setUp() {
        now(NOW);
        LocalDate today = NOW.toLocalDate();
        forecast = fixtures.forecast(today, today.atTime(18, 0), false);
    }

    @Test
    void 반경_300m_안의_입양자에게만_알린다() {
        Long open = openRequest(RequestFixtures.BASE_LAT, null);
        Long near = adopter("가까운집사", 299, "가깝몽");
        Long edge = adopter("경계집사", 300, "경계몽");
        Long far = adopter("먼집사", 301, "멀몽");

        notifyOpened(open);

        assertThat(fixtures.lastNotification(near, "SUBSTITUTE_OPEN")).isEqualTo(
                "근처 빗물받이가 도움이 필요해요 | 가깝몽 돌보러 가는 길에 근처 빗물받이도 같이 봐 주세요. "
                        + "오늘 17:00까지예요.");
        assertThat(fixtures.notificationCount(edge, "SUBSTITUTE_OPEN")).isEqualTo(1);
        assertThat(fixtures.notificationCount(far, "SUBSTITUTE_OPEN")).isZero();
    }

    @Test
    void 원래_입양자와_제재_중인_사용자는_제외한다() {
        Long owner = fixtures.user("원래집사");
        Long ownDrain = fixtures.drain(RequestFixtures.BASE_LAT, RequestFixtures.BASE_LNG, false);
        Long ownAdoption = fixtures.adoption(owner, ownDrain, "내몽");
        // 원래 입양자가 근처에 다른 빗물받이도 입양했어도 받지 않는다
        fixtures.adoption(owner,
                fixtures.drain(RequestFixtures.north(100), RequestFixtures.BASE_LNG, false), "둘째몽");
        Long sanctioned = adopter("제재집사", 50, "제재몽");
        fixtures.sanction(sanctioned);
        Long request = fixtures.request(forecast, ownDrain, ownAdoption, "OPEN",
                NOW.withHour(11), NOW.withHour(17));

        notifyOpened(request);

        assertThat(fixtures.notificationCount(owner, "SUBSTITUTE_OPEN")).isZero();
        assertThat(fixtures.notificationCount(sanctioned, "SUBSTITUTE_OPEN")).isZero();
    }

    @Test
    void 여러_요청은_사용자별_알림_1개로_묶는다() {
        Long first = openRequest(RequestFixtures.BASE_LAT, null);
        Long second = openRequest(RequestFixtures.north(150), null);
        Long helper = adopter("도움집사", 80, "도움몽");

        notifyOpened(first, second);

        assertThat(fixtures.notificationCount(helper, "SUBSTITUTE_OPEN")).isEqualTo(1);
        assertThat(fixtures.lastNotification(helper, "SUBSTITUTE_OPEN"))
                .startsWith("근처 또랑이 2곳이 도움이 필요해요 | 도움몽 돌보러 가는 길에");
    }

    private Long openRequest(double lat, Long adoption) {
        Long drain = fixtures.drain(lat, RequestFixtures.BASE_LNG, true);
        return fixtures.request(forecast, drain, adoption, "OPEN", NOW.withHour(11),
                NOW.withHour(17));
    }

    private Long adopter(String nickname, double metersNorth, String drainNickname) {
        Long user = fixtures.user(nickname);
        fixtures.adoption(user, fixtures.drain(RequestFixtures.north(metersNorth),
                RequestFixtures.BASE_LNG, false), drainNickname);
        return user;
    }

    private void notifyOpened(Long... requestIds) {
        transactionTemplate.executeWithoutResult(status -> substituteNotifier.notifyNearbyAdopters(
                inspectionRequestRepository.findAllById(List.of(requestIds))));
    }
}
