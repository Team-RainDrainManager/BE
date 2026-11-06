package com.rainbutler.domain.user.repository;

import com.rainbutler.domain.adoption.entity.ManagementActionType;
import com.rainbutler.domain.adoption.entity.ManagementType;
import com.rainbutler.domain.cleaning.entity.CleaningType;
import com.rainbutler.domain.user.entity.User;
import java.time.LocalDateTime;
import java.util.Collection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 내 정보 활동 통계(점검 수 · 봉사 시간)용 집계 쿼리입니다.
 *
 * <p>청소({@code cleaning})와 입양 후 점검({@code management_log})을 사용자별로 셉니다. 두 도메인의 저장소를
 * 건드리지 않도록 통계 쿼리만 여기에 모았습니다.
 *
 * <p>인정 기준: AI 인증을 통과했고(청소 COMPLETED · 점검 VERIFIED) 관리자가 반려(REJECTED)하지 않은 것.
 * 검토 대기(PENDING)도 인정합니다. 관리자 검토 기능이 생기기 전에는 모든 기록이 PENDING이기 때문입니다.
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
}
