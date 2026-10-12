package com.rainbutler.domain.adoption.repository;

import com.rainbutler.domain.adoption.entity.Adoption;
import com.rainbutler.domain.adoption.entity.AdoptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Adoption 엔티티 저장소입니다. 쿼리 메서드는 도메인 담당자가 추가합니다.
 */
public interface AdoptionRepository extends JpaRepository<Adoption, Long> {
    Optional<Adoption> findByDrain_IdAndStatus(
            Long drainId,
            AdoptionStatus status
    );
}
