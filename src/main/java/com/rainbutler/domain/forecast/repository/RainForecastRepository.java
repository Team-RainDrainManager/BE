package com.rainbutler.domain.forecast.repository;

import com.rainbutler.domain.forecast.entity.RainForecast;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * RainForecast 엔티티 저장소입니다.
 */
public interface RainForecastRepository extends JpaRepository<RainForecast, Long> {

    /**
     * 그 날짜의 진행 중(ACTIVE) 예보를 찾습니다. 실제 · 가상 예보는 따로 하나씩입니다 (부분 유니크 인덱스).
     *
     * @param rainDate 비 오는 날
     * @param simulated 가상 예보 여부
     * @return 진행 중 예보
     */
    @Query("SELECT f FROM RainForecast f WHERE f.rainDate = :rainDate "
            + "AND f.isSimulated = :simulated "
            + "AND f.status = com.rainbutler.domain.forecast.entity.ForecastStatus.ACTIVE")
    Optional<RainForecast> findActive(
            @Param("rainDate") LocalDate rainDate, @Param("simulated") boolean simulated);
}
