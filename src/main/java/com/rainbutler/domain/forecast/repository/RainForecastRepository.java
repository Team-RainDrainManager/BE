package com.rainbutler.domain.forecast.repository;

import com.rainbutler.domain.forecast.entity.RainForecast;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * RainForecast 엔티티 저장소입니다. 쿼리 메서드는 도메인 담당자가 추가합니다.
 */
public interface RainForecastRepository extends JpaRepository<RainForecast, Long> {
}
