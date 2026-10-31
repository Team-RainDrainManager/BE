package com.rainbutler.domain.forecast.controller;

import com.rainbutler.domain.forecast.dto.ForecastSimulateRequest;
import com.rainbutler.domain.forecast.dto.ForecastSimulateResponse;
import com.rainbutler.domain.request.service.ForecastSimulationService;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.response.ApiResponse;
import com.rainbutler.global.swagger.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 비 예보 관리자 API입니다.
 */
@Tag(name = "Admin", description = "관리자")
@RestController
@RequestMapping("/api/v1/admin/forecasts")
@RequiredArgsConstructor
public class ForecastAdminController {

    private static final String SIMULATE = """
            시연용 가상 예보를 적용합니다. **ADMIN만** 쓸 수 있습니다 (CITY_OFFICIAL은 403).

            실제 예보와 같은 기준(강수확률 최댓값 60% 이상 **그리고** 강수량 합계 5mm 이상)으로 판단해
            `rainStartAt` 날짜의 **가상 예보**를 확정 · 갱신 · 취소합니다. 실제 기상청 예보와는 따로 관리됩니다.

            - `applied`: `CONFIRMED`(새로 확정) · `UPDATED`(값 갱신) · `CANCELLED`(기준 미달로 취소) ·
              `IGNORED`(기준 미달, 진행 중 가상 예보 없음)
            - `rainStartAt`은 지금부터 3시간 뒤 이후여야 합니다.
            - `createdCount`: 바로 보낸 점검 요청 수. 내일 비를 06:00 전에 넣었거나 모레 이후 비면 06:00 일괄 발송
              대상이라 0입니다. (밤 21:00 ~ 06:00에 넣은 비도 06:00 배치가 보냅니다)
            - `cancelledCount`: 예보 취소로 취소된 점검 요청 수
            """;

    private final ForecastSimulationService forecastSimulationService;

    /**
     * 가상 예보를 적용합니다.
     */
    @Operation(summary = "가상 예보 적용 (시연용)", description = SIMULATE)
    @ApiErrorCodes({ErrorCode.INVALID_INPUT, ErrorCode.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN,
            ErrorCode.INVALID_TOKEN, ErrorCode.FORBIDDEN})
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/simulate")
    public ResponseEntity<ApiResponse<ForecastSimulateResponse>> simulate(
            @Valid @RequestBody ForecastSimulateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(forecastSimulationService.simulate(
                request.pop(), request.mm(), request.rainStartAt())));
    }
}
