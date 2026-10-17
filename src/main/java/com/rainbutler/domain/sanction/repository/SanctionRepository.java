package com.rainbutler.domain.sanction.repository;

import com.rainbutler.domain.sanction.entity.Sanction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Sanction 엔티티 저장소입니다.
 */
public interface SanctionRepository extends JpaRepository<Sanction, Long> {

    /**
     * 사용자의 제재 이력을 최신순으로 찾습니다. (해제된 제재 포함)
     *
     * @param userId 사용자 id
     * @return 제재 이력
     */
    List<Sanction> findAllByUser_IdOrderByStartsAtDescIdDesc(Long userId);

    /**
     * 해제되지 않은 제재 수를 셉니다. (누적 단계 계산용, 끝난 제재도 포함)
     *
     * @param userId 사용자 id
     * @return 해제되지 않은 제재 수
     */
    long countByUser_IdAndRevokedAtIsNull(Long userId);

    /**
     * 해제되지 않은 제재를 찾습니다. (지금 효력 판단은 서비스에서 시각으로 한다)
     *
     * @param userId 사용자 id
     * @return 해제되지 않은 제재
     */
    List<Sanction> findAllByUser_IdAndRevokedAtIsNull(Long userId);
}
