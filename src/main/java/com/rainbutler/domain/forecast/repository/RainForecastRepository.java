package com.rainbutler.domain.forecast.repository;

import com.rainbutler.domain.forecast.entity.RainForecast;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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

    /**
     * 그 날짜의 진행 중(ACTIVE) 예보를 실제 · 가상 모두 찾습니다. (06:00 일괄 발송)
     *
     * @param rainDate 비 오는 날
     * @return 예보 목록
     */
    @Query("SELECT f FROM RainForecast f WHERE f.rainDate = :rainDate "
            + "AND f.status = com.rainbutler.domain.forecast.entity.ForecastStatus.ACTIVE")
    List<RainForecast> findAllActiveByRainDate(@Param("rainDate") LocalDate rainDate);

    /**
     * 진행 중 예보 중 지금 이후 가장 이른 비 시작 시각을 찾습니다. (마감 당기기 기준)
     *
     * @param simulated 가상 예보 여부
     * @param now 현재 시각
     * @return 가장 이른 비 시작 (없으면 null)
     */
    @Query("SELECT MIN(f.rainStartAt) FROM RainForecast f WHERE f.isSimulated = :simulated "
            + "AND f.status = com.rainbutler.domain.forecast.entity.ForecastStatus.ACTIVE "
            + "AND f.rainStartAt > :now")
    LocalDateTime findEarliestActiveRainStartAfter(
            @Param("simulated") boolean simulated, @Param("now") LocalDateTime now);
}
