package com.rainbutler.domain.drain.repository;

import com.rainbutler.domain.adoption.entity.Adoption;
import com.rainbutler.domain.adoption.entity.AdoptionStatus;
import com.rainbutler.domain.drain.entity.Drain;
import com.rainbutler.domain.drain.entity.DrainDisplayStatus;
import com.rainbutler.domain.report.entity.Report;
import com.rainbutler.domain.report.entity.ReportStatus;
import com.rainbutler.domain.report.entity.ReportType;
import com.rainbutler.domain.request.dto.RainRequestStatus;
import com.rainbutler.domain.request.entity.InspectionRequest;
import com.rainbutler.domain.request.entity.RequestStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/**
 * 지도 · 목록 조회 조건입니다.
 *
 * <p>표시 상태는 DB 컬럼이 아니라 입양 · 제보를 조회 시점에 계산합니다. 목록 페이지네이션이 정확하도록
 * 같은 규칙을 SQL 조건으로 옮겼습니다.
 */
public final class DrainSpecifications {

    private DrainSpecifications() {
    }

    /**
     * 지도 사각형 안의 빗물받이를 찾습니다.
     */
    public static Specification<Drain> inBox(
            double minLatitude,
            double maxLatitude,
            double minLongitude,
            double maxLongitude) {
        return (root, query, cb) -> cb.and(
                cb.between(root.get("latitude"), minLatitude, maxLatitude),
                cb.between(root.get("longitude"), minLongitude, maxLongitude));
    }

    /**
     * 이름 · 주소 · 입양 별명으로 검색합니다. 검색어가 없으면 조건을 만들지 않습니다.
     *
     * @param keyword 검색어
     * @return 검색 조건 (검색어가 비어 있으면 null)
     */
    public static Specification<Drain> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";

        return (root, query, cb) -> {
            Expression<String> name = cb.lower(cb.coalesce(root.<String>get("name"), ""));
            Expression<String> address = cb.lower(cb.coalesce(root.<String>get("address"), ""));

            Subquery<Long> adoptedNickname = query.subquery(Long.class);
            Root<Adoption> adoption = adoptedNickname.from(Adoption.class);
            adoptedNickname.select(adoption.get("id")).where(
                    cb.equal(adoption.get("drain"), root),
                    cb.equal(adoption.get("status"), AdoptionStatus.ACTIVE),
                    cb.like(cb.lower(adoption.<String>get("nickname")), pattern));

            return cb.or(
                    cb.like(name, pattern),
                    cb.like(address, pattern),
                    cb.exists(adoptedNickname));
        };
    }

    /**
     * 화면 표시 상태로 거릅니다.
     *
     * <p>우선순위는 막힘 제보 → 미입양 → 점검 지연 → 정상입니다.
     *
     * @param status 표시 상태
     * @param now 점검 지연 판단 기준 시각
     * @return 상태 조건 (status가 null이면 null)
     */
    public static Specification<Drain> displayStatus(DrainDisplayStatus status, LocalDateTime now) {
        if (status == null) {
            return null;
        }

        return (root, query, cb) -> {
            Predicate hasBlockageReport = hasBlockageReport(root, query, cb);
            Predicate hasActiveAdoption = hasActiveAdoption(root, query, cb);
            Predicate isDelayed = hasDelayedAdoption(root, query, cb, now);

            return switch (status) {
                case BLOCKAGE_REPORTED -> hasBlockageReport;
                case AVAILABLE -> cb.and(cb.not(hasBlockageReport), cb.not(hasActiveAdoption));
                case MANAGEMENT_DELAYED -> cb.and(cb.not(hasBlockageReport), isDelayed);
                case NORMAL -> cb.and(
                        cb.not(hasBlockageReport), hasActiveAdoption, cb.not(isDelayed));
            };
        };
    }

    private static Predicate hasBlockageReport(
            Root<Drain> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        Subquery<Long> subquery = query.subquery(Long.class);
        Root<Report> report = subquery.from(Report.class);
        subquery.select(report.get("id")).where(
                cb.equal(report.get("drain"), root),
                cb.equal(report.get("type"), ReportType.BLOCKAGE),
                cb.or(
                        cb.equal(report.get("status"), ReportStatus.PENDING),
                        cb.and(
                                cb.isTrue(report.get("blocksAdoption")),
                                cb.equal(report.get("status"), ReportStatus.APPROVED))));
        return cb.exists(subquery);
    }

    private static Predicate hasActiveAdoption(
            Root<Drain> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        Subquery<Long> subquery = query.subquery(Long.class);
        Root<Adoption> adoption = subquery.from(Adoption.class);
        subquery.select(adoption.get("id")).where(
                cb.equal(adoption.get("drain"), root),
                cb.equal(adoption.get("status"), AdoptionStatus.ACTIVE));
        return cb.exists(subquery);
    }

    private static Predicate hasDelayedAdoption(
            Root<Drain> root, CriteriaQuery<?> query, CriteriaBuilder cb, LocalDateTime now) {
        Subquery<Long> subquery = query.subquery(Long.class);
        Root<Adoption> adoption = subquery.from(Adoption.class);
        subquery.select(adoption.get("id")).where(
                cb.equal(adoption.get("drain"), root),
                cb.equal(adoption.get("status"), AdoptionStatus.ACTIVE),
                cb.lessThan(adoption.get("nextManagementAt"), now));
        return cb.exists(subquery);
    }

    /**
     * 이번 비 대신 점검 상태로 빗물받이를 거릅니다. (지도 필터, {@code RainRequestLookup}과 같은 기준)
     *
     * <ul>
     *   <li>NEEDS_HELP · IN_PROGRESS: 그 상태의 진행 중 요청이 있음</li>
     *   <li>DONE: 오늘 완료한 요청이 있고 진행 중 요청은 없음</li>
     * </ul>
     *
     * @param status 지도 상태
     * @param completedSince 이 시각 이후 완료를 "오늘 완료"로 본다 (오늘 0시)
     * @return 조건
     */
    public static Specification<Drain> rainRequestStatus(
            RainRequestStatus status, LocalDateTime completedSince) {
        return (root, query, cb) -> {
            if (status != RainRequestStatus.DONE) {
                return cb.exists(requestSubquery(root, query, cb, status.requestStatuses(), null));
            }
            return cb.and(
                    cb.exists(requestSubquery(root, query, cb, status.requestStatuses(),
                            completedSince)),
                    cb.not(cb.exists(requestSubquery(root, query, cb, RequestStatus.IN_PROGRESS,
                            null))));
        };
    }

    private static Subquery<Long> requestSubquery(Root<Drain> root, CriteriaQuery<?> query,
            CriteriaBuilder cb, Collection<RequestStatus> statuses, LocalDateTime completedSince) {
        Subquery<Long> subquery = query.subquery(Long.class);
        Root<InspectionRequest> request = subquery.from(InspectionRequest.class);
        Predicate predicate = cb.and(
                cb.equal(request.get("drain"), root),
                request.get("status").in(statuses));
        if (completedSince != null) {
            predicate = cb.and(predicate, cb.greaterThanOrEqualTo(
                    request.<LocalDateTime>get("completedAt"), completedSince));
        }
        return subquery.select(request.get("id")).where(predicate);
    }
}
