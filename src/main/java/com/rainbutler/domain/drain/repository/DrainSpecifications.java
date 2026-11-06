package com.rainbutler.domain.drain.repository;

import com.rainbutler.domain.adoption.entity.Adoption;
import com.rainbutler.domain.adoption.entity.AdoptionStatus;
import com.rainbutler.domain.drain.entity.Drain;
import com.rainbutler.domain.drain.entity.DrainDisplayStatus;
import com.rainbutler.domain.report.entity.Report;
import com.rainbutler.domain.report.entity.ReportStatus;
import com.rainbutler.domain.report.entity.ReportType;
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
            Predicate hasPendingBlockage = hasPendingBlockage(root, query, cb);
            Predicate hasActiveAdoption = hasActiveAdoption(root, query, cb);
            Predicate isDelayed = hasDelayedAdoption(root, query, cb, now);

            return switch (status) {
                case BLOCKAGE_REPORTED -> hasPendingBlockage;
                case AVAILABLE -> cb.and(cb.not(hasPendingBlockage), cb.not(hasActiveAdoption));
                case MANAGEMENT_DELAYED -> cb.and(cb.not(hasPendingBlockage), isDelayed);
                case NORMAL -> cb.and(
                        cb.not(hasPendingBlockage), hasActiveAdoption, cb.not(isDelayed));
            };
        };
    }

    private static Predicate hasPendingBlockage(
            Root<Drain> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        Subquery<Long> subquery = query.subquery(Long.class);
        Root<Report> report = subquery.from(Report.class);
        subquery.select(report.get("id")).where(
                cb.equal(report.get("drain"), root),
                cb.equal(report.get("type"), ReportType.BLOCKAGE),
                cb.equal(report.get("status"), ReportStatus.PENDING));
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
     * 이번 비 예보의 요청이 주어진 상태인 빗물받이로 거릅니다. (지도 대신 점검 상태 필터)
     *
     * @param forecastId 이번 비 예보 id (없으면 결과 없음)
     * @param statuses 요청 상태
     * @return 조건
     */
    public static Specification<Drain> rainRequestStatus(
            Long forecastId, Collection<RequestStatus> statuses) {
        return (root, query, cb) -> {
            if (forecastId == null) {
                return cb.disjunction();
            }
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<InspectionRequest> request = subquery.from(InspectionRequest.class);
            subquery.select(request.get("id")).where(
                    cb.equal(request.get("drain"), root),
                    cb.equal(request.get("forecast").get("id"), forecastId),
                    request.get("status").in(statuses));
            return cb.exists(subquery);
        };
    }
}
