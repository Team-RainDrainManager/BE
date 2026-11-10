package com.rainbutler.domain.weather.controller;

import com.rainbutler.domain.weather.dto.SimulateRainRequest;
import com.rainbutler.domain.weather.dto.SimulateRainResponse;
import com.rainbutler.domain.weather.service.CurrentWeatherService;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.response.ApiResponse;
import com.rainbutler.global.swagger.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 현재 날씨 관리자 API입니다.
 */
@Tag(name = "Admin", description = "관리자")
@RestController
@RequestMapping("/api/v1/admin/weather")
@RequiredArgsConstructor
public class WeatherAdminController {

    private static final String SIMULATE = """
            시연용으로 지금부터 `minutes`분 동안 **비 오는 중**으로 표시합니다. **ADMIN만** 쓸 수 있습니다
            (CITY_OFFICIAL은 403).

            - 지도 비 예보 배너(`GET /api/v1/rain-notice`)가 이 기간 동안 `RAINING`이 됩니다.
            - `minutes` 0~360. **0이면 해제**하고 다시 실제 초단기실황으로 판단합니다.
            - 실제 관측 값(강수형태 · 강수량)은 바꾸지 않습니다.
            """;

    private final CurrentWeatherService currentWeatherService;
    private final Clock clock;

    /**
     * 시연용 "비 오는 중"을 켜거나 끕니다.
     */
    @Operation(summary = "비 오는 중 강제 (시연용)", description = SIMULATE)
    @ApiErrorCodes({ErrorCode.INVALID_INPUT, ErrorCode.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN,
            ErrorCode.INVALID_TOKEN, ErrorCode.FORBIDDEN})
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/simulate-rain")
    public ResponseEntity<ApiResponse<SimulateRainResponse>> simulateRain(
            @Valid @RequestBody SimulateRainRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime until = currentWeatherService.simulateRain(request.minutes(), now);
        return ResponseEntity.ok(ApiResponse.ok(new SimulateRainResponse(
                currentWeatherService.status(now).raining(), until)));
    }
}
