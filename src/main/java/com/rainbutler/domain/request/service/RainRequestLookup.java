package com.rainbutler.domain.request.service;

import com.rainbutler.domain.forecast.entity.RainForecast;
import com.rainbutler.domain.forecast.service.CurrentRainForecastFinder;
import com.rainbutler.domain.request.dto.RainRequestStatus;
import com.rainbutler.domain.request.dto.RainRequestSummary;
import com.rainbutler.domain.request.entity.InspectionRequest;
import com.rainbutler.domain.request.repository.InspectionRequestRepository;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 지도 빗물받이들의 이번 비 대신 점검 상태를 찾습니다. 이번 비 예보는 배너와 같은 기준
 * ({@link CurrentRainForecastFinder})으로 고릅니다.
 */
@Component
@RequiredArgsConstructor
public class RainRequestLookup {

    private final CurrentRainForecastFinder currentRainForecastFinder;
    private final InspectionRequestRepository inspectionRequestRepository;

    /**
     * 이번 비 예보를 찾습니다. (지도 필터용)
     *
     * @param today 오늘
     * @return 이번 비 예보 (없으면 비어 있음)
     */
    public Optional<RainForecast> currentForecast(LocalDate today) {
        return currentRainForecastFinder.find(today);
    }

    /**
     * 빗물받이들의 이번 비 요청 상태를 한 번의 조회로 찾습니다.
     *
     * @param drainIds 빗물받이 id
     * @param today 오늘
     * @return 빗물받이 id → 요청 요약 (이번 비 요청이 없거나 지도에 보여 주지 않는 상태면 빠짐)
     */
    public Map<Long, RainRequestSummary> findForDrains(Collection<Long> drainIds, LocalDate today) {
        Map<Long, RainRequestSummary> result = new HashMap<>();
        if (drainIds.isEmpty()) {
            return result;
        }
        Optional<RainForecast> forecast = currentRainForecastFinder.find(today);
        if (forecast.isEmpty()) {
            return result;
        }
        for (InspectionRequest request : inspectionRequestRepository
                .findAllByForecast_IdAndDrain_IdIn(forecast.get().getId(), drainIds)) {
            RainRequestStatus status = RainRequestStatus.of(request.getStatus());
            if (status != null) {
                result.put(request.getDrain().getId(),
                        new RainRequestSummary(request.getId(), status));
            }
        }
        return result;
    }
}
