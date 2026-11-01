package com.rainbutler.domain.adoption.controller;

import com.rainbutler.domain.adoption.dto.Response.MyAdoptionListResponse;
import com.rainbutler.domain.adoption.service.MyAdoptionQueryService;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.response.ApiResponse;
import com.rainbutler.global.security.LoginUserId;
import com.rainbutler.global.swagger.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 내 입양 목록 API입니다. */
@Tag(name = "Adoption", description = "입양")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/adoptions")
public class MyAdoptionController {

    private final MyAdoptionQueryService myAdoptionQueryService;

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
}
