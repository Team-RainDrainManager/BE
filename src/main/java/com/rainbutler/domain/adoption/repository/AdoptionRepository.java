package com.rainbutler.domain.adoption.repository;

import com.rainbutler.domain.adoption.entity.Adoption;
import com.rainbutler.domain.adoption.entity.AdoptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

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
     * 사용자의 입양 중 해당 상태인 것을 찾습니다. (점검 쉬기: ACTIVE 입양)
     *
     * @param userId 사용자 id
     * @param status 입양 상태
     * @return 입양 목록
     */
    List<Adoption> findAllByUser_IdAndStatus(Long userId, AdoptionStatus status);

    /**
     * 사용자의 입양 중 해당 상태인 것의 수를 셉니다. (내 정보: 입양 중인 빗물받이 수)
     *
     * @param userId 사용자 id
     * @param status 입양 상태
     * @return 입양 수
     */
    long countByUser_IdAndStatus(Long userId, AdoptionStatus status);
}
