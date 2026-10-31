package com.rainbutler.domain.request.service;

import com.rainbutler.domain.forecast.dto.ForecastSimulateResponse;
import com.rainbutler.domain.forecast.service.ForecastApplyResult;
import com.rainbutler.domain.forecast.service.ForecastApplyResult.Applied;
import com.rainbutler.domain.forecast.service.ForecastService;
import com.rainbutler.domain.request.entity.RequestStatus;
import com.rainbutler.domain.request.repository.InspectionRequestRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 가상 예보를 적용하고, 그 결과로 만들어지거나 취소된 점검 요청 수를 함께 돌려줍니다. (시연용)
 *
 * <p>트랜잭션을 열지 않습니다. 예보 적용이 커밋되면 요청 생성 · 취소 리스너가 같은 스레드에서 끝까지 실행되므로,
 * 그 뒤에 요청 수를 셉니다.
 */
@Service
@RequiredArgsConstructor
public class ForecastSimulationService {

    private final ForecastService forecastService;
    private final InspectionRequestRepository inspectionRequestRepository;

    /**
     * 가상 예보를 적용합니다.
     *
     * @param pop 강수확률 최댓값 (%)
     * @param mm 강수량 합계 (mm)
     * @param rainStartAt 비 시작 시각
     * @return 적용 결과와 생성 · 취소된 요청 수
     */
    public ForecastSimulateResponse simulate(int pop, BigDecimal mm, LocalDateTime rainStartAt) {
        ForecastApplyResult result = forecastService.simulate(pop, mm, rainStartAt);
        long created = result.applied() == Applied.CONFIRMED
                ? inspectionRequestRepository.countByForecast_Id(result.forecastId()) : 0;
        long cancelled = result.applied() == Applied.CANCELLED
                ? inspectionRequestRepository.countByForecast_IdAndStatus(
                        result.forecastId(), RequestStatus.CANCELLED) : 0;
        return ForecastSimulateResponse.of(result, (int) created, (int) cancelled);
    }
}
