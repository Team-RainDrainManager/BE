package com.rainbutler.domain.activity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.rainbutler.domain.activity.dto.ActivityListResponse;
import com.rainbutler.domain.activity.dto.ActivityResponse;
import com.rainbutler.domain.activity.entity.ActivityType;
import com.rainbutler.domain.drain.entity.Drain;
import com.rainbutler.domain.inspection.entity.Inspection;
import com.rainbutler.domain.inspection.entity.InspectionKind;
import com.rainbutler.domain.inspection.entity.InspectionStatus;
import com.rainbutler.domain.inspection.repository.InspectionRepository;
import com.rainbutler.domain.point.entity.PointHistory;
import com.rainbutler.domain.point.entity.PointSourceType;
import com.rainbutler.domain.point.entity.PointType;
import com.rainbutler.domain.point.repository.PointHistoryRepository;
import com.rainbutler.domain.report.entity.Report;
import com.rainbutler.domain.report.entity.ReportType;
import com.rainbutler.domain.report.repository.ReportRepository;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 활동 내역의 종류 필터 · 최신순 병합 · 커서 페이지 · 포인트 부호를 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class ActivityQueryServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long DRAIN_ID = 10L;

    @Mock
    private InspectionRepository inspectionRepository;
    @Mock
    private ReportRepository reportRepository;
    @Mock
    private PointHistoryRepository pointHistoryRepository;

    @InjectMocks
    private ActivityQueryService service;

    private User user;
    private Drain drain;

    @BeforeEach
    void setUp() {
        user = User.local(UUID.randomUUID() + "@test.com", "{noop}x", "테스트");
        ReflectionTestUtils.setField(user, "id", USER_ID);

        drain = Drain.builder().name("역곡천 1번").build();
        ReflectionTestUtils.setField(drain, "id", DRAIN_ID);
    }

    @Test
    void 점검_제보_경험치를_합쳐_최신순으로_내려준다() {
        LocalDateTime base = LocalDateTime.of(2026, 11, 4, 10, 0);
        Inspection inspection = inspection(1L, base.plusSeconds(1));
        Report report = report(2L, base.plusSeconds(2));
        PointHistory point = point(3L, base.plusSeconds(3));

        given(inspectionRepository.findPageByUserId(eq(USER_ID), any(), any(),
                eq(false), eq(false), any(Pageable.class))).willReturn(List.of(inspection));
        given(reportRepository.findPageByUserId(eq(USER_ID), any(), any(),
                eq(false), eq(false), any(Pageable.class))).willReturn(List.of(report));
        given(pointHistoryRepository.findPageByUserId(eq(USER_ID), any(), any(),
                eq(false), eq(false), any(Pageable.class))).willReturn(List.of(point));

        ActivityListResponse response = service.findMine(USER_ID, null, null, 20);

        assertThat(response.activities()).extracting(ActivityResponse::type)
                .containsExactly(ActivityType.POINT, ActivityType.REPORT, ActivityType.INSPECTION);
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void type을_지정하면_해당_종류만_조회한다() {
        LocalDateTime at = LocalDateTime.of(2026, 11, 4, 10, 0);
        Report report = report(2L, at);

        given(reportRepository.findPageByUserId(eq(USER_ID), any(), any(),
                eq(false), eq(false), any(Pageable.class))).willReturn(List.of(report));

        ActivityListResponse response = service.findMine(USER_ID, "report", null, 20);

        assertThat(response.activities()).extracting(ActivityResponse::type)
                .containsExactly(ActivityType.REPORT);
        verifyNoInteractions(inspectionRepository, pointHistoryRepository);
    }

    @Test
    void 점검_제목과_상태_경험치를_내려준다() {
        LocalDateTime at = LocalDateTime.of(2026, 11, 4, 10, 0);
        Inspection inspection = inspection(1L, at);

        given(inspectionRepository.findPageByUserId(eq(USER_ID), any(), any(),
                eq(false), eq(false), any(Pageable.class))).willReturn(List.of(inspection));

        ActivityResponse activity = service.findMine(USER_ID, "INSPECTION", null, 20)
                .activities().get(0);

        assertThat(activity.title()).isEqualTo("정기 점검");
        assertThat(activity.description()).isEqualTo("인증 완료");
        assertThat(activity.amount()).isEqualTo(5);
        assertThat(activity.drainId()).isEqualTo(DRAIN_ID);
        assertThat(activity.drainName()).isEqualTo("역곡천 1번");
        assertThat(activity.status()).isEqualTo("COMPLETED");
    }

    @Test
    void 경험치는_적립이면_양수_회수면_음수다() {
        LocalDateTime at = LocalDateTime.of(2026, 11, 4, 10, 0);
        PointHistory earn = point(1L, at, PointType.EARN, 5);
        PointHistory revoke = point(2L, at.plusSeconds(1), PointType.REVOKE, 3);

        given(pointHistoryRepository.findPageByUserId(eq(USER_ID), any(), any(),
                eq(false), eq(false), any(Pageable.class))).willReturn(List.of(revoke, earn));

        List<ActivityResponse> activities =
                service.findMine(USER_ID, "POINT", null, 20).activities();

        assertThat(activities).extracting(ActivityResponse::amount).containsExactly(-3, 5);
    }

    @Test
    void 다음_페이지가_있으면_커서를_주고_이어서_조회한다() {
        LocalDateTime base = LocalDateTime.of(2026, 11, 4, 10, 0);
        Inspection newest = inspection(3L, base.plusSeconds(3));
        Inspection middle = inspection(2L, base.plusSeconds(2));
        Inspection oldest = inspection(1L, base.plusSeconds(1));

        given(inspectionRepository.findPageByUserId(eq(USER_ID), any(), any(),
                eq(false), eq(false), any(Pageable.class)))
                .willReturn(List.of(newest, middle, oldest));

        ActivityListResponse first = service.findMine(USER_ID, "INSPECTION", null, 2);

        assertThat(first.activities()).extracting(ActivityResponse::id).containsExactly(3L, 2L);
        assertThat(first.nextCursor()).isEqualTo(cursorOf(middle) + "-INSPECTION-2");

        given(inspectionRepository.findPageByUserId(eq(USER_ID), eq(middle.getCreatedAt()),
                eq(2L), eq(true), eq(false), any(Pageable.class)))
                .willReturn(List.of(oldest));

        ActivityListResponse second =
                service.findMine(USER_ID, "INSPECTION", first.nextCursor(), 2);

        assertThat(second.activities()).extracting(ActivityResponse::id).containsExactly(1L);
        assertThat(second.nextCursor()).isNull();
    }

    @Test
    void 다른_종류_커서면_같은_시각을_모두_포함한다() {
        LocalDateTime at = LocalDateTime.of(2026, 11, 4, 10, 0);
        String cursor = cursorOf(inspection(2L, at)) + "-INSPECTION-2";

        given(reportRepository.findPageByUserId(eq(USER_ID), eq(at), eq(2L),
                eq(false), eq(true), any(Pageable.class))).willReturn(List.of());

        service.findMine(USER_ID, "REPORT", cursor, 20);

        verify(reportRepository).findPageByUserId(
                eq(USER_ID), eq(at), eq(2L), eq(false), eq(true), any(Pageable.class));
    }

    @Test
    void 잘못된_type은_INVALID_INPUT() {
        assertThatThrownBy(() -> service.findMine(USER_ID, "SOCIAL", null, 20))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void 잘못된_cursor는_INVALID_INPUT() {
        assertThatThrownBy(() -> service.findMine(USER_ID, null, "abc", 20))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void size가_1보다_작으면_INVALID_INPUT() {
        assertThatThrownBy(() -> service.findMine(USER_ID, null, null, 0))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
    }

    private Inspection inspection(Long id, LocalDateTime createdAt) {
        Inspection inspection = Inspection.builder()
                .user(user)
                .drain(drain)
                .kind(InspectionKind.ROUTINE)
                .beforePhotoKey("before-key")
                .capturedAt(createdAt)
                .build();
        ReflectionTestUtils.setField(inspection, "id", id);
        ReflectionTestUtils.setField(inspection, "createdAt", createdAt);
        ReflectionTestUtils.setField(inspection, "status", InspectionStatus.COMPLETED);
        ReflectionTestUtils.setField(inspection, "xpAwarded", 5);
        return inspection;
    }

    private Report report(Long id, LocalDateTime createdAt) {
        Report report = Report.builder()
                .user(user)
                .drain(drain)
                .type(ReportType.BLOCKAGE)
                .description("막혀 있어요")
                .build();
        ReflectionTestUtils.setField(report, "id", id);
        ReflectionTestUtils.setField(report, "createdAt", createdAt);
        return report;
    }

    private PointHistory point(Long id, LocalDateTime createdAt) {
        return point(id, createdAt, PointType.EARN, 5);
    }

    private PointHistory point(Long id, LocalDateTime createdAt, PointType type, int amount) {
        PointHistory point = PointHistory.builder()
                .user(user)
                .sourceType(PointSourceType.INSPECTION)
                .sourceId(1L)
                .amount(amount)
                .type(type)
                .description("정기 점검")
                .build();
        ReflectionTestUtils.setField(point, "id", id);
        ReflectionTestUtils.setField(point, "createdAt", createdAt);
        return point;
    }

    private long cursorOf(Inspection inspection) {
        return inspection.getCreatedAt().toEpochSecond(ZoneOffset.UTC) * 1_000_000L
                + inspection.getCreatedAt().getNano() / 1_000L;
    }
}
