package com.rainbutler.domain.request.repository;

import com.rainbutler.domain.drain.entity.Drain;
import com.rainbutler.domain.request.entity.InspectionRequest;
import com.rainbutler.domain.request.entity.RequestStatus;
import com.rainbutler.domain.user.entity.User;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * InspectionRequest 엔티티 저장소입니다. 쿼리 메서드는 도메인 담당자가 추가합니다.
 */
public interface InspectionRequestRepository extends JpaRepository<InspectionRequest, Long> {

    /**
     * 입양들에 걸린 요청 중 해당 상태인 것을 찾습니다. (입양 해제 시 재공개할 SENT · ACCEPTED 요청)
     *
     * @param adoptionIds 입양 id 목록
     * @param statuses 요청 상태
     * @return 요청 목록
     */
    List<InspectionRequest> findAllByAdoption_IdInAndStatusIn(
            Collection<Long> adoptionIds, Collection<RequestStatus> statuses);

    /**
     * 사용자가 대신 점검을 맡은 요청 중 해당 상태인 것을 찾습니다. (탈퇴 시 재공개할 CLAIMED 요청)
     *
     * @param userId 대신 점검자 id
     * @param status 요청 상태
     * @return 요청 목록
     */
    List<InspectionRequest> findAllBySubstituteUser_IdAndStatus(Long userId, RequestStatus status);

    /**
     * 새 요청을 만들면 안 되는 빗물받이 id를 찾습니다. 이 예보로 이미 요청이 있거나(완료 · 취소 포함),
     * 다른 예보의 요청이 아직 진행 중인 빗물받이입니다.
     *
     * @param forecastId 예보 id
     * @param inProgress 진행 중 상태
     * @return 빗물받이 id
     */
    @Query("SELECT r.drain.id FROM InspectionRequest r "
            + "WHERE r.forecast.id = :forecastId OR r.status IN :inProgress")
    Set<Long> findBlockedDrainIds(@Param("forecastId") Long forecastId,
            @Param("inProgress") Collection<RequestStatus> inProgress);

    /**
     * 입양되지 않은 운영 중 빗물받이를 모두 찾습니다. (비 예보 때 바로 대신 점검으로 공개할 대상)
     *
     * <p>빗물받이는 공공데이터로 일괄 등록해 사진 · AI 판정(위험 여부)이 없으므로 위험 여부로 거르지 않습니다.
     *
     * @return 빗물받이 목록
     */
    @Query("SELECT d FROM Drain d "
            + "WHERE d.status = com.rainbutler.domain.drain.entity.DrainStatus.ACTIVE "
            + "AND NOT EXISTS (SELECT 1 FROM Adoption a WHERE a.drain = d "
            + "AND a.status = com.rainbutler.domain.adoption.entity.AdoptionStatus.ACTIVE)")
    List<Drain> findUnadoptedActiveDrains();

    /**
     * 예보의 요청 중 해당 상태인 것을 찾습니다. (예보 취소)
     *
     * @param forecastId 예보 id
     * @param statuses 상태
     * @return 요청 목록
     */
    List<InspectionRequest> findAllByForecast_IdAndStatusIn(
            Long forecastId, Collection<RequestStatus> statuses);

    /**
     * 예보로 만든 요청 수를 셉니다. (가상 예보 API 응답)
     *
     * @param forecastId 예보 id
     * @return 요청 수
     */
    long countByForecast_Id(Long forecastId);

    /**
     * 예보의 요청 중 해당 상태인 것을 셉니다. (가상 예보 API 응답)
     *
     * @param forecastId 예보 id
     * @param status 상태
     * @return 요청 수
     */
    long countByForecast_IdAndStatus(Long forecastId, RequestStatus status);

    /**
     * 완료 마감이 지난 진행 중 요청 id를 마감이 이른 순으로 찾습니다. (마감 처리 배치)
     *
     * @param inProgress 진행 중 상태
     * @param now 현재 시각
     * @param pageable 한 번에 처리할 개수
     * @return 요청 id
     */
    @Query("SELECT r.id FROM InspectionRequest r WHERE r.status IN :inProgress "
            + "AND r.completeDeadlineAt <= :now ORDER BY r.completeDeadlineAt")
    List<Long> findIdsCompleteOverdue(@Param("inProgress") Collection<RequestStatus> inProgress,
            @Param("now") LocalDateTime now, Pageable pageable);

    /**
     * 응답 마감이 지난 SENT 요청 id를 마감이 이른 순으로 찾습니다. (마감 처리 배치)
     *
     * @param now 현재 시각
     * @param pageable 한 번에 처리할 개수
     * @return 요청 id
     */
    @Query("SELECT r.id FROM InspectionRequest r "
            + "WHERE r.status = com.rainbutler.domain.request.entity.RequestStatus.SENT "
            + "AND r.replyDeadlineAt <= :now ORDER BY r.replyDeadlineAt")
    List<Long> findIdsReplyOverdue(@Param("now") LocalDateTime now, Pageable pageable);

    /**
     * 완료 마감이 주어진 시각보다 늦은 진행 중 요청을 찾습니다. (비가 앞당겨져 마감을 당길 대상)
     *
     * @param simulated 가상 예보의 요청이면 true
     * @param inProgress 진행 중 상태
     * @param deadline 새 완료 마감
     * @return 요청 목록
     */
    @Query("SELECT r FROM InspectionRequest r WHERE r.forecast.isSimulated = :simulated "
            + "AND r.status IN :inProgress AND r.completeDeadlineAt > :deadline")
    List<InspectionRequest> findInProgressDeadlineAfter(@Param("simulated") boolean simulated,
            @Param("inProgress") Collection<RequestStatus> inProgress,
            @Param("deadline") LocalDateTime deadline);

    /**
     * 사용자가 기간 안에 대신 점검을 수락한 횟수를 셉니다. 수락 뒤 취소 · 놓침이 된 것도 셉니다. (하루 제한)
     *
     * @param userId 수락자 id
     * @param from 시작 (포함)
     * @param to 끝 (제외)
     * @return 수락 횟수
     */
    long countBySubstituteUser_IdAndClaimedAtGreaterThanEqualAndClaimedAtLessThan(
            Long userId, LocalDateTime from, LocalDateTime to);

    /**
     * 공개된 요청을 선착순으로 맡습니다. 조건부 UPDATE 한 번이라 동시에 수락해도 한 명만 성공합니다.
     *
     * @param requestId 요청 id
     * @param substitute 수락자
     * @param now 수락 시각
     * @return 바뀐 행 수 (0이면 이미 누가 맡았거나 마감 지남)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE InspectionRequest r SET "
            + "r.status = com.rainbutler.domain.request.entity.RequestStatus.CLAIMED, "
            + "r.substituteUser = :substitute, r.claimedAt = :now, r.updatedAt = :now "
            + "WHERE r.id = :requestId "
            + "AND r.status = com.rainbutler.domain.request.entity.RequestStatus.OPEN "
            + "AND r.completeDeadlineAt > :now AND r.deletedAt IS NULL")
    int claimIfOpen(@Param("requestId") Long requestId, @Param("substitute") User substitute,
            @Param("now") LocalDateTime now);

    /**
     * 내 ACTIVE 입양의 요청 중 진행 중이거나 기준 시각 뒤에 끝난 것을 찾습니다. 취소된 요청은 뺍니다. (내 요청 목록)
     *
     * @param userId 입양자 id
     * @param inProgress 진행 중 상태
     * @param since 이 시각 이후에 완료 · 놓침 처리된 것까지 포함
     * @return 요청 목록 (빗물받이 · 예보 · 입양 함께)
     */
    @Query("SELECT r FROM InspectionRequest r JOIN FETCH r.drain JOIN FETCH r.forecast "
            + "JOIN FETCH r.adoption a WHERE a.user.id = :userId "
            + "AND a.status = com.rainbutler.domain.adoption.entity.AdoptionStatus.ACTIVE "
            + "AND (r.status IN :inProgress OR r.completedAt >= :since OR r.missedAt >= :since)")
    List<InspectionRequest> findMineAsOwner(@Param("userId") Long userId,
            @Param("inProgress") Collection<RequestStatus> inProgress,
            @Param("since") LocalDateTime since);

    /**
     * 내가 대신 점검으로 맡은 요청 중 진행 중이거나 기준 시각 뒤에 끝난 것을 찾습니다. (내 요청 목록)
     *
     * @param userId 수락자 id
     * @param inProgress 진행 중 상태
     * @param since 이 시각 이후에 완료 · 놓침 처리된 것까지 포함
     * @return 요청 목록 (빗물받이 · 예보 · 입양 함께)
     */
    @Query("SELECT r FROM InspectionRequest r JOIN FETCH r.drain JOIN FETCH r.forecast "
            + "LEFT JOIN FETCH r.adoption WHERE r.substituteUser.id = :userId "
            + "AND (r.status IN :inProgress OR r.completedAt >= :since OR r.missedAt >= :since)")
    List<InspectionRequest> findMineAsClaimant(@Param("userId") Long userId,
            @Param("inProgress") Collection<RequestStatus> inProgress,
            @Param("since") LocalDateTime since);

    /**
     * 위경도 사각형 안 빗물받이의 완료 마감 전 공개(OPEN) 요청을 찾습니다. 정확한 반경은 호출하는 쪽에서 거릅니다.
     * (내 요청 목록의 근처 요청)
     *
     * @param now 현재 시각
     * @return 요청 목록 (빗물받이 · 예보 · 입양 함께)
     */
    @Query("SELECT r FROM InspectionRequest r JOIN FETCH r.drain d JOIN FETCH r.forecast "
            + "LEFT JOIN FETCH r.adoption "
            + "WHERE r.status = com.rainbutler.domain.request.entity.RequestStatus.OPEN "
            + "AND r.completeDeadlineAt > :now "
            + "AND d.latitude BETWEEN :minLat AND :maxLat "
            + "AND d.longitude BETWEEN :minLng AND :maxLng")
    List<InspectionRequest> findOpenInBox(@Param("now") LocalDateTime now,
            @Param("minLat") double minLat, @Param("maxLat") double maxLat,
            @Param("minLng") double minLng, @Param("maxLng") double maxLng);

    /**
     * 완료 마감이 지난 미입양 · 위험 아님 빗물받이의 진행 중 요청을 한 번에 EXPIRED로 끝냅니다. (마감 처리 배치)
     *
     * <p>미입양 대신 점검은 수백 ~ 수천 건이 같은 마감을 가지므로 건별이 아니라 UPDATE 한 번으로 처리합니다.
     * 알림이 없어서 가능합니다. 종료 시각은 {@code missed_at}에 남깁니다.
     *
     * @param inProgress 진행 중 상태
     * @param now 현재 시각
     * @return 끝낸 요청 수
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE InspectionRequest r SET "
            + "r.status = com.rainbutler.domain.request.entity.RequestStatus.EXPIRED, "
            + "r.missedAt = :now, r.updatedAt = :now "
            + "WHERE r.status IN :inProgress AND r.completeDeadlineAt <= :now "
            + "AND r.adoption IS NULL AND r.deletedAt IS NULL "
            + "AND r.drain.id IN (SELECT d.id FROM Drain d WHERE d.isRisk = false)")
    int expireUnadoptedOverdue(@Param("inProgress") Collection<RequestStatus> inProgress,
            @Param("now") LocalDateTime now);

    /**
     * 위경도 사각형 안에서 지금 도움이 필요한(완료 마감 전 OPEN) 빗물받이 위치를 찾습니다. 같은 빗물받이는 한 번만
     * 나옵니다. 정확한 반경은 호출하는 쪽에서 거릅니다. (근처 도움 필요 개수)
     *
     * @param now 현재 시각
     * @return 빗물받이 id · 위경도
     */
    @Query("SELECT DISTINCT new com.rainbutler.domain.request.repository.OpenDrainPoint("
            + "d.id, d.latitude, d.longitude) FROM InspectionRequest r JOIN r.drain d "
            + "WHERE r.status = com.rainbutler.domain.request.entity.RequestStatus.OPEN "
            + "AND r.completeDeadlineAt > :now "
            + "AND d.latitude BETWEEN :minLat AND :maxLat "
            + "AND d.longitude BETWEEN :minLng AND :maxLng")
    List<OpenDrainPoint> findOpenDrainsInBox(@Param("now") LocalDateTime now,
            @Param("minLat") double minLat, @Param("maxLat") double maxLat,
            @Param("minLng") double minLng, @Param("maxLng") double maxLng);

    /**
     * 예보의 요청 수를 상태별로 셉니다. (지도 비 예보 배너의 대신 점검 진행 상황)
     *
     * @param forecastId 예보 id
     * @return [상태, 개수] 목록
     */
    @Query("SELECT r.status, COUNT(r) FROM InspectionRequest r WHERE r.forecast.id = :forecastId "
            + "GROUP BY r.status")
    List<Object[]> countByStatus(@Param("forecastId") Long forecastId);

    /**
     * 예보의 진행 중 요청 중 가장 이른 완료 마감을 찾습니다. (지도 비 예보 배너)
     *
     * @param forecastId 예보 id
     * @param inProgress 진행 중 상태
     * @return 가장 이른 완료 마감 (진행 중 요청이 없으면 null)
     */
    @Query("SELECT MIN(r.completeDeadlineAt) FROM InspectionRequest r "
            + "WHERE r.forecast.id = :forecastId AND r.status IN :inProgress")
    LocalDateTime findEarliestCompleteDeadline(@Param("forecastId") Long forecastId,
            @Param("inProgress") Collection<RequestStatus> inProgress);
}
