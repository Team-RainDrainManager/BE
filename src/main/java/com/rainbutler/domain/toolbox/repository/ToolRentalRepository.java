package com.rainbutler.domain.toolbox.repository;

import com.rainbutler.domain.toolbox.entity.RentalStatus;
import com.rainbutler.domain.toolbox.entity.ToolRental;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * ToolRental 엔티티 저장소입니다. 쿼리 메서드는 도메인 담당자가 추가합니다.
 */
public interface ToolRentalRepository extends JpaRepository<ToolRental, Long> {

    /**
     * 사용자에게 해당 상태의 대여가 있는지 확인합니다. (회원 탈퇴: 대여 중인 도구)
     *
     * @param userId 사용자 id
     * @param status 대여 상태
     * @return 존재 여부
     */
    boolean existsByUser_IdAndStatus(Long userId, RentalStatus status);
}
