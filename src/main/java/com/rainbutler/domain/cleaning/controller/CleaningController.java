package com.rainbutler.domain.cleaning.controller;

import com.rainbutler.domain.cleaning.dto.Request.CleaningStartRequest;
import com.rainbutler.domain.cleaning.dto.Request.CleaningVerifyRequest;
import com.rainbutler.domain.cleaning.dto.Response.CleaningStartResponse;
import com.rainbutler.domain.cleaning.dto.Response.CleaningVerifyResponse;
import com.rainbutler.domain.cleaning.service.CleaningService;
import com.rainbutler.global.response.ApiResponse;
import com.rainbutler.global.security.LoginUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Cleaning", description = "청소")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/drains/{drainId}/cleanings")
public class CleaningController {

    private final CleaningService cleaningService;

    @Operation(summary = "입양 청소 시작", description = "빗물받이 입양을 위한 청소를 시작합니다. "
            + "촬영 위치가 빗물받이에서 50m 넘게 떨어져 있으면 400 CLEANING_LOCATION_MISMATCH로 "
            + "거부하므로 빗물받이 앞에서 다시 촬영해야 합니다. AI 판정 결과에 따라 입양 여부가 결정됩니다.")
    @PostMapping
    public ApiResponse<CleaningStartResponse> startAdoptionCleaning(
            @LoginUserId Long userId,
            @PathVariable Long drainId,
            @Valid @RequestBody CleaningStartRequest request
    ) {
        CleaningStartResponse response = cleaningService.startAdoptionCleaning(userId, drainId, request);

        return ApiResponse.ok(response);
    }
}
