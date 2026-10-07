package com.rainbutler.domain.user.repository;

import com.rainbutler.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * User 엔티티 저장소입니다. 쿼리 메서드는 도메인 담당자가 추가합니다.
 */
public interface UserRepository extends JpaRepository<User, Long> {
}
