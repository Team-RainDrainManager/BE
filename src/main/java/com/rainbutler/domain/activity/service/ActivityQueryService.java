package com.rainbutler.domain.activity.service;

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
import com.rainbutler.domain.report.entity.ReportStatus;
import com.rainbutler.domain.report.entity.ReportType;
import com.rainbutler.domain.report.repository.ReportRepository;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 활동 내역(점검 · 제보 · 경험치)을 한 목록으로 조회합니다.
 *
 * <p>세 테이블을 각각 {@code (createdAt, id)} 커서로 읽어 메모리에서 합칩니다. 정렬은 최신순이며, 같은
 * 시각이면 {@link ActivityType} 선언 순서(점검 → 제보 → 경험치), 그다음 id 내림차순입니다. 한 테이블에서
 * 페이지 크기 + 1건만 읽어도 합친 결과의 다음 페이지 유무를 판단할 수 있습니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityQueryService {

    /** 한 페이지 기본 개수 */
    public static final int DEFAULT_SIZE = 20;
    /** 한 페이지 최대 개수 (넘으면 이 값으로 줄인다) */
    public static final int MAX_SIZE = 50;

    /** 커서가 없을 때 모든 행을 포함시키기 위한 미래 시각 */
    private static final LocalDateTime NO_CURSOR_TIME = LocalDateTime.of(9999, 12, 31, 23, 59, 59);

    private static final Comparator<ActivityResponse> NEWEST_FIRST =
            Comparator.comparing(ActivityResponse::occurredAt).reversed()
                    .thenComparingInt(activity -> activity.type().ordinal())
                    .thenComparing(ActivityResponse::id, Comparator.reverseOrder());

    private final InspectionRepository inspectionRepository;
    private final ReportRepository reportRepository;
    private final PointHistoryRepository pointHistoryRepository;

    /**
     * 내 활동 내역을 최신순으로 한 페이지 조회합니다.
     *
     * @param userId 로그인 사용자 id
     * @param type 활동 종류 (null · {@code ALL}이면 전체, INSPECTION · REPORT · POINT)
     * @param cursor 이전 페이지의 {@code nextCursor} (첫 페이지는 null)
     * @param size 페이지 크기 (null이면 20, 50 초과면 50)
     * @return 활동 내역 한 페이지
     * @throws BusinessException type · cursor · size가 잘못된 경우 (INVALID_INPUT)
     */
    public ActivityListResponse findMine(Long userId, String type, String cursor, Integer size) {
        int pageSize = size == null ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        if (pageSize < 1) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "size는 1 이상이어야 해요.");
        }
        ActivityType filter = parseType(type);
        Cursor parsedCursor = cursor == null || cursor.isBlank() ? null : Cursor.parse(cursor);

        LocalDateTime cursorTime = parsedCursor == null ? NO_CURSOR_TIME : parsedCursor.occurredAt();
        Long cursorId = parsedCursor == null ? Long.MAX_VALUE : parsedCursor.id();
        Pageable pageable = PageRequest.of(0, pageSize + 1);

        List<ActivityResponse> rows = new ArrayList<>();
        if (filter == null || filter == ActivityType.INSPECTION) {
            rows.addAll(inspectionRepository.findPageByUserId(
                            userId, cursorTime, cursorId,
                            sameType(ActivityType.INSPECTION, parsedCursor),
                            afterType(ActivityType.INSPECTION, parsedCursor),
                            pageable)
                    .stream()
                    .map(this::inspection)
                    .toList());
        }
        if (filter == null || filter == ActivityType.REPORT) {
            rows.addAll(reportRepository.findPageByUserId(
                            userId, cursorTime, cursorId,
                            sameType(ActivityType.REPORT, parsedCursor),
                            afterType(ActivityType.REPORT, parsedCursor),
                            pageable)
                    .stream()
                    .map(this::report)
                    .toList());
        }
        if (filter == null || filter == ActivityType.POINT) {
            rows.addAll(pointHistoryRepository.findPageByUserId(
                            userId, cursorTime, cursorId,
                            sameType(ActivityType.POINT, parsedCursor),
                            afterType(ActivityType.POINT, parsedCursor),
                            pageable)
                    .stream()
                    .map(this::point)
                    .toList());
        }

        rows.sort(NEWEST_FIRST);
        boolean hasNext = rows.size() > pageSize;
        List<ActivityResponse> page = rows.stream().limit(pageSize).toList();
        String nextCursor = hasNext ? encodeCursor(page.get(page.size() - 1)) : null;
        return new ActivityListResponse(nextCursor, page);
    }

    private ActivityType parseType(String type) {
        if (type == null || type.isBlank() || "ALL".equalsIgnoreCase(type.strip())) {
            return null;
        }
        try {
            return ActivityType.valueOf(type.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "type은 ALL · INSPECTION · REPORT · POINT 중 하나여야 해요.");
        }
    }

    private boolean sameType(ActivityType type, Cursor cursor) {
        return cursor != null && cursor.type() == type;
    }

    private boolean afterType(ActivityType type, Cursor cursor) {
        return cursor != null && type.ordinal() > cursor.type().ordinal();
    }

    private String encodeCursor(ActivityResponse activity) {
        long micros = activity.occurredAt().toEpochSecond(ZoneOffset.UTC) * 1_000_000L
                + activity.occurredAt().getNano() / 1_000L;
        return micros + "-" + activity.type().name() + "-" + activity.id();
    }

    private ActivityResponse inspection(Inspection inspection) {
        Drain drain = inspection.getDrain();
        String drainName = inspection.getAdoption() == null
                ? drain.getName()
                : inspection.getAdoption().getNickname();
        Integer xp = inspection.getXpAwarded() != null && inspection.getXpAwarded() > 0
                ? inspection.getXpAwarded()
                : null;
        return new ActivityResponse(
                ActivityType.INSPECTION,
                inspection.getId(),
                inspection.getCreatedAt(),
                kindLabel(inspection.getKind()),
                inspectionStatusLabel(inspection.getStatus()),
                xp,
                drain.getId(),
                drainName,
                inspection.getStatus().name());
    }

    private ActivityResponse report(Report report) {
        Drain drain = report.getDrain();
        return new ActivityResponse(
                ActivityType.REPORT,
                report.getId(),
                report.getCreatedAt(),
                reportTypeLabel(report.getType()),
                reportStatusLabel(report.getStatus()),
                null,
                drain == null ? null : drain.getId(),
                drain == null ? null : drain.getName(),
                report.getStatus().name());
    }

    private ActivityResponse point(PointHistory point) {
        int amount = point.getType() == PointType.EARN
                ? Math.abs(point.getAmount())
                : -Math.abs(point.getAmount());
        return new ActivityResponse(
                ActivityType.POINT,
                point.getId(),
                point.getCreatedAt(),
                point.getDescription() == null ? "경험치" : point.getDescription(),
                sourceLabel(point.getSourceType()),
                amount,
                null,
                null,
                null);
    }

    private String kindLabel(InspectionKind kind) {
        return switch (kind) {
            case ROUTINE -> "정기 점검";
            case FORECAST -> "비 예보 점검";
            case SUBSTITUTE -> "대신 점검";
        };
    }

    private String inspectionStatusLabel(InspectionStatus status) {
        return switch (status) {
            case NEEDS_CLEANING -> "청소 필요";
            case COMPLETED -> "인증 완료";
            case PENDING -> "검토 대기";
            case REJECTED -> "반려";
        };
    }

    private String reportTypeLabel(ReportType type) {
        return switch (type) {
            case BLOCKAGE -> "막힘 제보";
            case NEW_DRAIN -> "신규 빗물받이 제보";
            case LOCATION_FIX -> "위치 수정 제보";
            case REMOVAL -> "철거 제보";
            case ODOR -> "악취 제보";
        };
    }

    private String reportStatusLabel(ReportStatus status) {
        return switch (status) {
            case PENDING -> "처리 대기";
            case APPROVED -> "승인 완료";
            case REJECTED -> "반려";
        };
    }

    private String sourceLabel(PointSourceType sourceType) {
        return switch (sourceType) {
            case CLEANING -> "청소";
            case MANAGEMENT -> "관리";
            case INSPECTION -> "점검";
            case REPORT -> "제보";
        };
    }

    /**
     * 활동 목록 커서입니다. {@code {epochMicros}-{type}-{id}} 형식의 문자열로 주고받습니다.
     */
    private record Cursor(LocalDateTime occurredAt, ActivityType type, Long id) {

        static Cursor parse(String value) {
            String[] parts = value.split("-");
            if (parts.length != 3) {
                throw invalid();
            }
            try {
                long micros = Long.parseLong(parts[0]);
                ActivityType type = ActivityType.valueOf(parts[1]);
                long id = Long.parseLong(parts[2]);
                if (id < 1) {
                    throw invalid();
                }
                LocalDateTime occurredAt = LocalDateTime.ofEpochSecond(
                        Math.floorDiv(micros, 1_000_000L),
                        (int) Math.floorMod(micros, 1_000_000L) * 1_000,
                        ZoneOffset.UTC);
                return new Cursor(occurredAt, type, id);
            } catch (BusinessException e) {
                throw e;
            } catch (RuntimeException e) {
                throw invalid();
            }
        }

        private static BusinessException invalid() {
            return new BusinessException(ErrorCode.INVALID_INPUT,
                    "cursor가 올바르지 않아요. 응답의 nextCursor를 그대로 보내 주세요.");
        }
    }
}
