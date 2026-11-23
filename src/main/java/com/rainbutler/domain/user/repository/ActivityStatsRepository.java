package com.rainbutler.domain.user.repository;

import com.rainbutler.domain.adoption.entity.ManagementActionType;
import com.rainbutler.domain.adoption.entity.ManagementType;
import com.rainbutler.domain.cleaning.entity.CleaningType;
import com.rainbutler.domain.inspection.entity.InspectionKind;
import com.rainbutler.domain.user.entity.User;
import java.time.LocalDateTime;
import java.util.Collection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 내 정보 활동 통계(점검 수 · 봉사 시간)용 집계 쿼리입니다.
 *
 * <p>점검({@code inspection})과 예전 기록(청소 {@code cleaning}의 대신 점검 · 입양 후 점검 {@code management_log})을
 * 함께 사용자별로 셉니다. 예전 기록은 더 이상 새로 생기지 않습니다 (점검은 모두 {@code inspection}에 저장).
 * 각 도메인의 저장소를 건드리지 않도록 통계 쿼리만 여기에 모았습니다.
 *
 * <p>인정 기준
 * <ul>
 *   <li>점검({@code inspection}): COMPLETED만. 청소 후 사진 대기 · 관리자 검토 대기 · 반려는 세지 않는다</li>
 *   <li>예전 기록: AI 인증을 통과했고(청소 COMPLETED · 점검 VERIFIED) 관리자가 반려(REJECTED)하지 않은 것.
 *       검토 대기(PENDING)도 인정한다 (관리자 검토 기능이 생기기 전에는 모든 기록이 PENDING이었음)</li>
 * </ul>
 */
public interface ActivityStatsRepository extends Repository<User, Long> {

    /**
     * 사용자가 완료한 청소 수를 셉니다.
     *
     * @param userId 사용자 id
     * @param types 셀 청소 종류 (입양 전 청소 · 대신 점검)
     * @return 완료하고 반려되지 않은 청소 수
     */
    @Query("SELECT COUNT(c) FROM Cleaning c WHERE c.user.id = :userId AND c.type IN :types "
            + "AND c.status = com.rainbutler.domain.cleaning.entity.CleaningStatus.COMPLETED "
            + "AND c.reviewStatus <> com.rainbutler.global.entity.ReviewStatus.REJECTED")
    long countCompletedCleanings(
            @Param("userId") Long userId, @Param("types") Collection<CleaningType> types);

    /**
     * 사용자가 입양한 빗물받이에서 인증된 점검 수를 셉니다. (입양 해제된 빗물받이 기록도 포함)
     *
     * @param userId 사용자 id
     * @param actionTypes 셀 점검 방식 (살펴보기만 · 청소까지)
     * @return 인증되고 반려되지 않은 점검 수
     */
    @Query("SELECT COUNT(m) FROM ManagementLog m WHERE m.adoption.user.id = :userId "
            + "AND m.actionType IN :actionTypes "
            + "AND m.status = com.rainbutler.domain.adoption.entity.ManagementStatus.VERIFIED "
            + "AND m.reviewStatus <> com.rainbutler.global.entity.ReviewStatus.REJECTED")
    long countVerifiedManagements(@Param("userId") Long userId,
            @Param("actionTypes") Collection<ManagementActionType> actionTypes);

    /**
     * 사용자의 인증된 관리 기록을 종류별로 셉니다. (활동 확인서의 정기 · 비 예보 구분)
     *
     * @param userId 사용자 id
     * @param type 관리 종류 (정기 · 비 예보)
     * @return 인증되고 반려되지 않은 관리 수
     */
    @Query("SELECT COUNT(m) FROM ManagementLog m WHERE m.adoption.user.id = :userId "
            + "AND m.type = :type "
            + "AND m.status = com.rainbutler.domain.adoption.entity.ManagementStatus.VERIFIED "
            + "AND m.reviewStatus <> com.rainbutler.global.entity.ReviewStatus.REJECTED")
    long countVerifiedManagementsByType(
            @Param("userId") Long userId, @Param("type") ManagementType type);

    /**
     * 사용자가 처음 완료한 청소 시각입니다. (활동 확인서의 활동 기간)
     *
     * @param userId 사용자 id
     * @param types 셀 청소 종류
     * @return 첫 청소 시각 (없으면 null)
     */
    @Query("SELECT MIN(c.createdAt) FROM Cleaning c WHERE c.user.id = :userId AND c.type IN :types "
            + "AND c.status = com.rainbutler.domain.cleaning.entity.CleaningStatus.COMPLETED "
            + "AND c.reviewStatus <> com.rainbutler.global.entity.ReviewStatus.REJECTED")
    LocalDateTime findFirstCleaningAt(
            @Param("userId") Long userId, @Param("types") Collection<CleaningType> types);

    /**
     * 사용자가 처음 인증받은 관리 시각입니다. (활동 확인서의 활동 기간)
     *
     * @param userId 사용자 id
     * @param actionTypes 셀 점검 방식
     * @return 첫 관리 시각 (없으면 null)
     */
    @Query("SELECT MIN(m.createdAt) FROM ManagementLog m WHERE m.adoption.user.id = :userId "
            + "AND m.actionType IN :actionTypes "
            + "AND m.status = com.rainbutler.domain.adoption.entity.ManagementStatus.VERIFIED "
            + "AND m.reviewStatus <> com.rainbutler.global.entity.ReviewStatus.REJECTED")
    LocalDateTime findFirstManagementAt(@Param("userId") Long userId,
            @Param("actionTypes") Collection<ManagementActionType> actionTypes);

    /**
     * 사용자가 완료한 점검 수를 종류별로 셉니다. ({@code inspection} 테이블, 실제로 점검한 사람 기준)
     *
     * @param userId 사용자 id
     * @param kinds 셀 점검 종류 (정기 · 비 예보 · 대신 점검)
     * @return 인증을 통과한(COMPLETED) 점검 수
     */
    @Query("SELECT COUNT(i) FROM Inspection i WHERE i.user.id = :userId AND i.kind IN :kinds "
            + "AND i.status = com.rainbutler.domain.inspection.entity.InspectionStatus.COMPLETED")
    long countCompletedInspections(
            @Param("userId") Long userId, @Param("kinds") Collection<InspectionKind> kinds);

    /**
     * 사용자가 처음 완료한 점검 시각입니다. (활동 확인서의 활동 기간)
     *
     * @param userId 사용자 id
     * @return 첫 점검 시각 (없으면 null)
     */
    @Query("SELECT MIN(i.createdAt) FROM Inspection i WHERE i.user.id = :userId "
            + "AND i.status = com.rainbutler.domain.inspection.entity.InspectionStatus.COMPLETED")
    LocalDateTime findFirstInspectionAt(@Param("userId") Long userId);
}
