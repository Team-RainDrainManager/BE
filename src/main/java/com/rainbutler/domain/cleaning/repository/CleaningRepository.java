package com.rainbutler.domain.cleaning.repository;

import com.rainbutler.domain.cleaning.entity.Cleaning;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Cleaning 엔티티 저장소입니다. 쿼리 메서드는 도메인 담당자가 추가합니다.
 */
public interface CleaningRepository extends JpaRepository<Cleaning, Long> {
}
