package com.rainbutler.domain.terms.repository;

import com.rainbutler.domain.terms.entity.Terms;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Terms 엔티티 저장소입니다.
 */
public interface TermsRepository extends JpaRepository<Terms, Long> {

    /**
     * 현재 약관 목록을 표시 순서대로 찾습니다. (type별로 시행 중인 최신 버전)
     *
     * @param now 기준 시각 (이 시각까지 시행된 버전만)
     * @return 현재 약관 목록
     */
    @Query("SELECT t FROM Terms t "
            + "WHERE t.effectiveAt <= :now AND t.version = ("
            + "  SELECT MAX(t2.version) FROM Terms t2 "
            + "  WHERE t2.type = t.type AND t2.effectiveAt <= :now) "
            + "ORDER BY t.displayOrder")
    List<Terms> findCurrent(@Param("now") LocalDateTime now);
}
