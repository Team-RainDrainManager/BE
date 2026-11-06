package com.rainbutler.domain.drain.controller;

import com.rainbutler.domain.drain.dto.Request.DrainAssessmentRequest;
import com.rainbutler.domain.drain.dto.Request.DrainCreateRequest;
import com.rainbutler.domain.drain.dto.Response.DrainAssessmentResponse;
import com.rainbutler.domain.drain.dto.Response.DrainCreateResponse;
import com.rainbutler.domain.drain.dto.Response.DrainDetailResponse;
import com.rainbutler.domain.drain.dto.Response.DrainResponse;
import com.rainbutler.domain.drain.dto.Response.GrowthResponse;
import com.rainbutler.domain.drain.entity.DrainDisplayStatus;
import com.rainbutler.domain.drain.service.DrainService;
import com.rainbutler.domain.request.dto.RainRequestStatus;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.response.ApiResponse;
import com.rainbutler.global.response.PageResponse;
import com.rainbutler.global.security.LoginUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Drain", description = "빗물받이")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/drain")
public class DrainController {

    private static final String RAIN_STATUS_DESCRIPTION = "각 빗물받이에 이번 비(오늘 · 내일 비 예보) 대신 점검 "
            + "상태 `rainRequest`(`requestId`, `status`: NEEDS_HELP 필요 · IN_PROGRESS 진행 중 · DONE 완료, "
            + "없으면 null)를 붙입니다. `rainStatus`(NEEDS_HELP · IN_PROGRESS · DONE)로 거를 수 있고, "
            + "기존 `status` 필터와 함께 쓸 수 있습니다. 이번 비 예보가 없으면 `rainStatus` 필터 결과는 비어 있습니다.";

    private final DrainService drainService;

    @Operation(summary = "지도 위 빗물받이 목록 조회",
            description = "경도/위도 범위 내 빗물받이 목록을 조회합니다. 표시 상태 · 검색어로 거를 수 있습니다. "
                    + RAIN_STATUS_DESCRIPTION)
    @GetMapping
    public ApiResponse<List<DrainResponse>> getDrainList(
            @RequestParam Double minLatitude,
            @RequestParam Double maxLatitude,
            @RequestParam Double minLongitude,
            @RequestParam Double maxLongitude,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String rainStatus
    ) {
        List<DrainResponse> drains = drainService.getDrainList(
                minLatitude,
                maxLatitude,
                minLongitude,
                maxLongitude,
                parseStatus(status),
                q,
                parseRainStatus(rainStatus)
        );

        return ApiResponse.ok(drains);
    }

    @Operation(summary = "빗물받이 목록 조회 (페이지)",
            description = "이름 · 주소 · 입양 별명으로 검색하고 표시 상태로 거른 목록을 페이지로 조회합니다. "
                    + RAIN_STATUS_DESCRIPTION)
    @GetMapping("/list")
    public ApiResponse<PageResponse<DrainResponse>> getDrainPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String rainStatus
    ) {
        PageResponse<DrainResponse> response = drainService.getDrainPage(
                page, size, parseStatus(status), q, parseRainStatus(rainStatus));

        return ApiResponse.ok(response);
    }

    @Operation(summary = "빗물받이 상세 조회",
            description = "빗물받이 ID로 기본 정보와 현재 입양 정보를 조회합니다. 로그인하면 내 빗물받이인지도 알려줍니다.")
    @GetMapping("/{drainId}")
    public ApiResponse<DrainDetailResponse> getDrainDetail(
            @LoginUserId(required = false) Long userId,
            @PathVariable Long drainId
    ) {
        DrainDetailResponse response = drainService.getDrainDetail(userId, drainId);

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

    @Operation(summary = "성장 조회", description = "빗물받이 입양 귀속의 레벨·점수·다음 레벨 진행률을 조회합니다. 로그인이 필요합니다.")
    @GetMapping("/{drainId}/growth")
    public ApiResponse<GrowthResponse> getGrowth(
            @LoginUserId Long userId,
            @PathVariable Long drainId
    ) {
        GrowthResponse response = drainService.getGrowth(drainId);

        return ApiResponse.ok(response);
    }

    /**
     * 표시 상태 파라미터를 enum으로 바꿉니다. 영문 이름 · 지도 키(unadopted · normal · overdue ·
     * blocked) · 화면 라벨(한글)을 모두 받습니다.
     */
    private RainRequestStatus parseRainStatus(String rainStatus) {
        try {
            return RainRequestStatus.from(rainStatus);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "대신 점검 상태는 NEEDS_HELP · IN_PROGRESS · DONE 중 하나여야 합니다.");
        }
    }

    private DrainDisplayStatus parseStatus(String status) {
        try {
            return DrainDisplayStatus.from(status);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "표시 상태는 unadopted · normal · overdue · blocked 중 하나여야 합니다.");
        }
    }
}
