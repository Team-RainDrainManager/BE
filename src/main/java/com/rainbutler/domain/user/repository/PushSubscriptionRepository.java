package com.rainbutler.domain.user.repository;

import com.rainbutler.domain.user.entity.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * PushSubscription 엔티티 저장소입니다. 쿼리 메서드는 도메인 담당자가 추가합니다.
 */
public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {
}
