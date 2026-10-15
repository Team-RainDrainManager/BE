package com.rainbutler.domain.drain.controller;

import com.rainbutler.domain.drain.dto.Request.DrainAssessmentRequest;
import com.rainbutler.domain.drain.dto.Request.DrainCreateRequest;
import com.rainbutler.domain.drain.dto.Response.DrainAssessmentResponse;
import com.rainbutler.domain.drain.dto.Response.DrainCreateResponse;
import com.rainbutler.domain.drain.dto.Response.DrainDetailResponse;
import com.rainbutler.domain.drain.dto.Response.DrainResponse;
import com.rainbutler.domain.drain.service.DrainService;
import com.rainbutler.global.response.ApiResponse;
import com.rainbutler.global.security.LoginUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

    @Operation(summary = "빗물받이 생성", description = "현장에서 촬영한 사진과 위치로 빗물받이를 등록합니다. 더러움 단계는 서버 AI 판정으로 결정됩니다.")
    @PostMapping
    public ApiResponse<DrainCreateResponse> createDrain(
            @LoginUserId Long userId,
            @Valid @RequestBody DrainCreateRequest request
    ) {
        DrainCreateResponse response = drainService.createDrain(userId, request);

        return ApiResponse.ok(response);
    }

    @Operation(summary = "빗물받이 사진 판독", description = "사진 1장을 AI로 판독합니다. 저장하지 않으며, 생성·점검·청소 확인이 같은 판정값을 씁니다.")
    @PostMapping("/assessments")
    public ApiResponse<DrainAssessmentResponse> assessPhoto(
            @LoginUserId Long userId,
            @Valid @RequestBody DrainAssessmentRequest request
    ) {
        DrainAssessmentResponse response = drainService.assessPhoto(userId, request);

        return ApiResponse.ok(response);
    }

}
