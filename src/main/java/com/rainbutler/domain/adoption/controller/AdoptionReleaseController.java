package com.rainbutler.domain.adoption.controller;

import com.rainbutler.domain.adoption.dto.Request.AdoptionReleaseRequest;
import com.rainbutler.domain.adoption.dto.Response.AdoptionReleaseResponse;
import com.rainbutler.domain.adoption.service.AdoptionService;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.response.ApiResponse;
import com.rainbutler.global.security.LoginUserId;
import com.rainbutler.global.swagger.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Adoption", description = "입양")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/adoptions/{adoptionId}/release")
public class AdoptionReleaseController {

    private final AdoptionService adoptionService;

    @Operation(summary = "입양 해제", description = "본인 입양을 해제(파양)합니다. 기록 · 성장은 보관되고, "
            + "입양자에게 간 점검 요청은 대신 점검으로 다시 공개됩니다. 로그인이 필요합니다.")
    @ApiErrorCodes({ErrorCode.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN, ErrorCode.INVALID_TOKEN,
            ErrorCode.INVALID_INPUT, ErrorCode.FORBIDDEN, ErrorCode.ADOPTION_NOT_FOUND,
            ErrorCode.DUPLICATE_RESOURCE})
    @PostMapping
    public ApiResponse<AdoptionReleaseResponse> releaseAdoption(
            @LoginUserId Long userId,
            @PathVariable Long adoptionId,
            @Valid @RequestBody AdoptionReleaseRequest request
    ) {
        AdoptionReleaseResponse response =
                adoptionService.releaseAdoption(userId, adoptionId, request);

        return ApiResponse.ok(response);
    }
}
