package com.rainbutler.domain.adoption.repository;

import com.rainbutler.domain.adoption.entity.Adoption;
import com.rainbutler.domain.adoption.entity.AdoptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Adoption 엔티티 저장소입니다. 쿼리 메서드는 도메인 담당자가 추가합니다.
 */
public interface AdoptionRepository extends JpaRepository<Adoption, Long> {
    Optional<Adoption> findByDrain_IdAndStatus(
            Long drainId,
            AdoptionStatus status
    );

    /**
     * 여러 빗물받이의 ACTIVE 입양을 한 번에 찾습니다. (지도 · 목록의 레벨 표시)
     *
     * @param drainIds 빗물받이 id 목록
     * @return ACTIVE 입양 목록
     */
    @Query("SELECT a FROM Adoption a WHERE a.drain.id IN :drainIds "
            + "AND a.status = com.rainbutler.domain.adoption.entity.AdoptionStatus.ACTIVE")
    List<Adoption> findActiveByDrainIds(@Param("drainIds") List<Long> drainIds);

    /**
     * 사용자의 입양 중 해당 상태인 것을 찾습니다. (점검 쉬기: ACTIVE 입양)
     *
     * @param userId 사용자 id
     * @param status 입양 상태
     * @return 입양 목록
     */
    List<Adoption> findAllByUser_IdAndStatus(Long userId, AdoptionStatus status);

    /**
     * 해당 별명을 쓰는 입양이 있는지 확인합니다. (별명 중복 확인)
     *
     * <p>대소문자를 구분하지 않고 비교합니다. 삭제된 입양은 {@code @SQLRestriction}으로 빠집니다.
     *
     * @param nickname 별명 (앞뒤 공백 제거본)
     * @param status 입양 상태
     * @return 있으면 true
     */
    boolean existsByNicknameIgnoreCaseAndStatus(String nickname, AdoptionStatus status);

    /**
     * 해당 별명을 쓰는 다른 입양이 있는지 확인합니다. (별명 변경 시 본인 입양 제외)
     *
     * <p>대소문자를 구분하지 않고 비교합니다.
     *
     * @param nickname 별명 (앞뒤 공백 제거본)
     * @param status 입양 상태
     * @param id 제외할 입양 id
     * @return 있으면 true
     */
    boolean existsByNicknameIgnoreCaseAndStatusAndIdNot(
            String nickname, AdoptionStatus status, Long id);

    /**
     * 내 입양 목록을 최근 입양한 순으로 찾습니다.
     *
     * @param userId 사용자 id
     * @param status 입양 상태
     * @return 입양 목록
     */
    List<Adoption> findAllByUser_IdAndStatusOrderByAdoptedAtDesc(Long userId,
            AdoptionStatus status);

    /**
     * 사용자의 입양 중 해당 상태인 것의 수를 셉니다. (내 정보: 입양 중인 빗물받이 수)
     *
     * @param userId 사용자 id
     * @param status 입양 상태
     * @return 입양 수
     */
    long countByUser_IdAndStatus(Long userId, AdoptionStatus status);

    /**
     * 사용자가 입양했던 빗물받이들의 성장 점수 합입니다. (봉사 시간 산정 · 해제한 입양도 포함)
     *
     * @param userId 사용자 id
     * @return 빗물받이 성장 점수 합
     */
    @Query("SELECT COALESCE(SUM(a.score), 0) FROM Adoption a WHERE a.user.id = :userId")
    long sumScoreByUser(@Param("userId") Long userId);

    /**
     * 해당 상태인 입양 id를 모두 찾습니다. (미관리 경고 · 자동 해제 · 관리 예정 알림 배치)
     *
     * @param status 입양 상태
     * @return 입양 id 목록
     */
    @Query("SELECT a.id FROM Adoption a WHERE a.status = :status")
    List<Long> findIdsByStatus(@Param("status") AdoptionStatus status);


    /**
     * 운영 중 빗물받이의 ACTIVE 입양을 빗물받이 · 입양자와 함께 모두 찾습니다. (비 예보 요청 발송 대상)
     *
     * @return 입양 목록
     */
    @Query("SELECT a FROM Adoption a JOIN FETCH a.drain d JOIN FETCH a.user "
            + "WHERE a.status = com.rainbutler.domain.adoption.entity.AdoptionStatus.ACTIVE "
            + "AND d.status = com.rainbutler.domain.drain.entity.DrainStatus.ACTIVE")
    List<Adoption> findAllActiveOnActiveDrains();
}
