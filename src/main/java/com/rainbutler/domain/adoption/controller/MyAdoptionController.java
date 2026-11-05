package com.rainbutler.domain.adoption.controller;

import com.rainbutler.domain.adoption.dto.Response.AdoptionDetailResponse;
import com.rainbutler.domain.adoption.dto.Response.MyAdoptionListResponse;
import com.rainbutler.domain.adoption.service.MyAdoptionQueryService;
import com.rainbutler.domain.inspection.dto.InspectionListResponse;
import com.rainbutler.domain.inspection.service.InspectionQueryService;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.response.ApiResponse;
import com.rainbutler.global.security.LoginUserId;
import com.rainbutler.global.swagger.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 내 입양 목록 API입니다. */
@Tag(name = "Adoption", description = "입양")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/adoptions")
public class MyAdoptionController {

    private final MyAdoptionQueryService myAdoptionQueryService;
    private final InspectionQueryService inspectionQueryService;

    /** 현재 입양 중인 빗물받이 목록을 조회합니다. */
    @Operation(summary = "내 입양 목록 조회",
            description = "현재 입양(ACTIVE) 중인 빗물받이를 최근 입양 순으로 조회합니다. "
                    + "lastManagedAt은 입양일과 마지막 승인 점검일 중 늦은 시각이며, "
                    + "28일 이상 관리하지 않으면 releaseWarning이 true입니다.")
    @ApiErrorCodes({ErrorCode.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN, ErrorCode.INVALID_TOKEN,
            ErrorCode.USER_NOT_FOUND})
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MyAdoptionListResponse>> findMine(
            @LoginUserId Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(myAdoptionQueryService.findMine(userId)));
    }

    @Operation(summary = "입양 상세 조회", description = "입양 상세와 돌봄 정보를 조회합니다. "
            + "다음 관리 예정일까지 남은 일수(daysUntilNextManagement, 지났으면 음수)와 위험 빗물받이 여부, "
            + "최신 현장 사진을 함께 내려줍니다. 입양자 · 관리자(ADMIN · CITY_OFFICIAL)만 볼 수 있고, "
            + "해제된 입양은 조회할 수 없습니다.")
    @ApiErrorCodes({ErrorCode.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN, ErrorCode.INVALID_TOKEN,
            ErrorCode.USER_NOT_FOUND, ErrorCode.ADOPTION_NOT_FOUND, ErrorCode.FORBIDDEN})
    @GetMapping("/{adoptionId}")
    public ResponseEntity<ApiResponse<AdoptionDetailResponse>> findDetail(
            @LoginUserId Long userId,
            @PathVariable Long adoptionId) {
        return ResponseEntity.ok(ApiResponse.ok(
                myAdoptionQueryService.findDetail(userId, adoptionId)));
    }

    @Operation(summary = "입양별 점검 이력 조회", description = "입양의 점검 기록을 최신순으로 커서 페이지로 조회합니다. "
            + "입양자 · 관리자(ADMIN · CITY_OFFICIAL)만 볼 수 있습니다. 사진은 조회 URL로 내려주며, "
            + "촬영일로부터 보존 기간(365일)이 지나면 photoDeleted=true이고 사진 URL은 null입니다. "
            + "로그인이 필요합니다.")
    @ApiErrorCodes({ErrorCode.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN, ErrorCode.INVALID_TOKEN,
            ErrorCode.USER_NOT_FOUND, ErrorCode.ADOPTION_NOT_FOUND, ErrorCode.FORBIDDEN,
            ErrorCode.INVALID_INPUT})
    @GetMapping("/{adoptionId}/inspections")
    public ResponseEntity<ApiResponse<InspectionListResponse>> findInspections(
            @LoginUserId Long userId,
            @PathVariable Long adoptionId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(ApiResponse.ok(
                inspectionQueryService.findByAdoption(userId, adoptionId, cursor, size)));
    }
}
