package com.rainbutler.domain.drain.repository;

import com.rainbutler.domain.drain.entity.Drain;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Drain 엔티티 저장소입니다. 쿼리 메서드는 도메인 담당자가 추가합니다.
 */
public interface DrainRepository extends JpaRepository<Drain, Long> {
}
