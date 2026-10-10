package com.rainbutler.domain.drain.controller;

import com.rainbutler.domain.drain.dto.Response.DrainResponse;
import com.rainbutler.domain.drain.repository.DrainRepository;
import com.rainbutler.domain.drain.service.DrainService;
import com.rainbutler.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "빗물받이", description = "빗물받이 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/drain")
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

}
