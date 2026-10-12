package com.rainbutler.domain.drain.controller;

import com.rainbutler.domain.drain.dto.Response.DrainDetailResponse;
import com.rainbutler.domain.drain.dto.Response.DrainResponse;
import com.rainbutler.domain.drain.service.DrainService;
import com.rainbutler.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Drain", description = "빗물받이")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/drain")
public class DrainController {

    private final DrainService drainService;

    @Operation(summary = "지도 위 빗물받이 목록 조회", description = "경도/위도 범위 내 빗물받이 목록을 조회합니다.")
    @GetMapping
    public ApiResponse<List<DrainResponse>> getDrainList(
            @RequestParam Double minLatitude,
            @RequestParam Double maxLatitude,
            @RequestParam Double minLongitude,
            @RequestParam Double maxLongitude
    ) {
        List<DrainResponse> drains = drainService.getDrainList(
                minLatitude,
                maxLatitude,
                minLongitude,
                maxLongitude
        );

        return ApiResponse.ok(drains);
    }

    @Operation(summary = "빗물받이 상세 조회", description = "빗물받이 ID로 기본 정보와 현재 입양 정보를 조회합니다")
    @GetMapping("/{drainId}")
    public ApiResponse<DrainDetailResponse> getDrainDetail(
            @PathVariable Long drainId
    ) {
        DrainDetailResponse response = drainService.getDrainDetail(drainId);

        return ApiResponse.ok(response);
    }

}
