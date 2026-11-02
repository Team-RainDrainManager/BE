package com.rainbutler.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rainbutler.domain.request.RequestFixtures;
import com.rainbutler.domain.user.entity.Role;
import com.rainbutler.global.security.JwtTokenProvider;
import com.rainbutler.support.IntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 알림 목록 · 읽음 · 모두 읽음 API를 검증합니다.
 */
@AutoConfigureMockMvc
class NotificationApiTest extends IntegrationTestSupport {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2027, 12, 1, 10, 0);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Clock clock;

    private RequestFixtures fixtures;
    private Long me;
    private Long other;

    @BeforeEach
    void setUp() {
        given(clock.getZone()).willReturn(SEOUL);
        given(clock.instant()).willReturn(NOW.atZone(SEOUL).toInstant());
        fixtures = new RequestFixtures(jdbcTemplate);
        me = fixtures.user("나");
        other = fixtures.user("남");
    }

    @Test
    void 최신순으로_커서를_이어_받고_마지막_페이지는_nextCursor가_null이다() throws Exception {
        List<Long> ids = notifications(me, 5, false);
        notifications(other, 2, false);

        list("size", "2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(5))
                .andExpect(jsonPath("$.data.notifications[*].notificationId")
                        .value(contains(ids.get(4).intValue(), ids.get(3).intValue())))
                .andExpect(jsonPath("$.data.nextCursor").value(ids.get(3).intValue()));
        list("size", "2", "cursor", String.valueOf(ids.get(3)))
                .andExpect(jsonPath("$.data.notifications[*].notificationId")
                        .value(contains(ids.get(2).intValue(), ids.get(1).intValue())))
                .andExpect(jsonPath("$.data.nextCursor").value(ids.get(1).intValue()));
        list("size", "2", "cursor", String.valueOf(ids.get(1)))
                .andExpect(jsonPath("$.data.notifications[*].notificationId")
                        .value(contains(ids.get(0).intValue())))
                .andExpect(jsonPath("$.data.nextCursor").value(nullValue()));
    }

    @Test
    void 알림_내용과_관련_id를_내려준다() throws Exception {
        Long id = jdbcTemplate.queryForObject("INSERT INTO notification (user_id, type, title, "
                + "body, created_at, updated_at) VALUES (?, 'RAIN_TOMORROW', '내일 비 소식이 있어요 ☔', "
                + "'또랑이를 살펴봐 주세요.', ?, ?) RETURNING id", Long.class, me,
                Timestamp.valueOf(NOW), Timestamp.valueOf(NOW));

        list()
                .andExpect(jsonPath("$.data.notifications[0].notificationId").value(id))
                .andExpect(jsonPath("$.data.notifications[0].type").value("RAIN_TOMORROW"))
                .andExpect(jsonPath("$.data.notifications[0].title").value("내일 비 소식이 있어요 ☔"))
                .andExpect(jsonPath("$.data.notifications[0].body").value("또랑이를 살펴봐 주세요."))
                .andExpect(jsonPath("$.data.notifications[0].createdAt")
                        .value("2027-12-01T10:00:00"))
                .andExpect(jsonPath("$.data.notifications[0].read").value(false))
                .andExpect(jsonPath("$.data.notifications[0].requestId").value(nullValue()))
                .andExpect(jsonPath("$.data.notifications[0].adoptionId").value(nullValue()));
    }

    @Test
    void unread_true면_안_읽은_것만_준다() throws Exception {
        notifications(me, 2, true);
        List<Long> unread = notifications(me, 1, false);

        list("unread", "true")
                .andExpect(jsonPath("$.data.notifications", hasSize(1)))
                .andExpect(jsonPath("$.data.notifications[0].notificationId")
                        .value(unread.get(0).intValue()))
                .andExpect(jsonPath("$.data.unreadCount").value(1));
        list().andExpect(jsonPath("$.data.notifications", hasSize(3)));
    }

    @Test
    void size는_기본_20_최대_50이고_1보다_작으면_400이다() throws Exception {
        notifications(me, 55, false);

        list().andExpect(jsonPath("$.data.notifications", hasSize(20)));
        list("size", "100").andExpect(jsonPath("$.data.notifications", hasSize(50)));
        list("size", "0")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void 내_알림을_읽음으로_표시하고_다시_읽어도_200이다() throws Exception {
        Long id = notifications(me, 1, false).get(0);

        read(me, id).andExpect(status().isOk()).andExpect(jsonPath("$.data").value(nullValue()));
        Timestamp firstRead = readAt(id);
        assertThat(firstRead).isEqualTo(Timestamp.valueOf(NOW));

        given(clock.instant()).willReturn(NOW.plusHours(1).atZone(SEOUL).toInstant());
        read(me, id).andExpect(status().isOk());
        assertThat(readAt(id)).isEqualTo(firstRead);
    }

    @Test
    void 남의_알림이나_없는_알림을_읽으면_404다() throws Exception {
        Long othersId = notifications(other, 1, false).get(0);

        read(me, othersId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOTIFICATION_NOT_FOUND"));
        read(me, 999_999_999L).andExpect(status().isNotFound());
        assertThat(readAt(othersId)).isNull();
    }

    @Test
    void 모두_읽음은_내_안_읽은_알림만_바꾸고_개수를_준다() throws Exception {
        Long alreadyRead = notifications(me, 1, true).get(0);
        Timestamp before = readAt(alreadyRead);
        notifications(me, 3, false);
        Long othersId = notifications(other, 1, false).get(0);

        mockMvc.perform(patch("/api/v1/notifications/read-all").header("Authorization",
                        "Bearer " + token(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updatedCount").value(3));

        list().andExpect(jsonPath("$.data.unreadCount").value(0));
        assertThat(readAt(alreadyRead)).isEqualTo(before);
        assertThat(readAt(othersId)).isNull();
    }

    private List<Long> notifications(Long userId, int count, boolean read) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(jdbcTemplate.queryForObject("INSERT INTO notification (user_id, type, title, "
                    + "body, read_at, created_at, updated_at) VALUES (?, 'SUBSTITUTE_OPEN', ?, "
                    + "'내용', ?, now(), now()) RETURNING id", Long.class, userId, "알림" + i,
                    read ? Timestamp.valueOf(NOW.minusDays(1)) : null));
        }
        return ids;
    }

    private ResultActions list(String... params) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/notifications")
                .header("Authorization", "Bearer " + token(me));
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request);
    }

    private ResultActions read(Long userId, Long notificationId) throws Exception {
        return mockMvc.perform(patch("/api/v1/notifications/" + notificationId + "/read")
                .header("Authorization", "Bearer " + token(userId)));
    }

    private Timestamp readAt(Long notificationId) {
        return jdbcTemplate.queryForObject("SELECT read_at FROM notification WHERE id = ?",
                Timestamp.class, notificationId);
    }

    private String token(Long userId) {
        return jwtTokenProvider.createAccessToken(userId, Role.USER);
    }
}
