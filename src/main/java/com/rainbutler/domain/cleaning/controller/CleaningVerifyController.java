package com.rainbutler.domain.cleaning.controller;

import com.rainbutler.domain.cleaning.dto.Request.CleaningVerifyRequest;
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
@RequestMapping("/api/v1/cleanings")
public class CleaningVerifyController {

    private final CleaningService cleaningService;

    @Operation(summary = "입양 청소 완료 인증", description = "입양 전 첫 청소(B-5) 흐름의 청소 완료 인증입니다. "
            + "청소 후 사진의 촬영 위치가 빗물받이에서 50m를 넘거나, 청소 전 사진 위치와 50m 넘게 "
            + "차이 나면 400 CLEANING_LOCATION_MISMATCH로 거부합니다. "
            + "청소 완료 후 AI로 인증을 진행합니다. afterGrade < beforeGrade이면 통과하며, "
            + "통과해야 입양 등록(B-7)이 가능합니다. 실패 시 재시도할 수 있습니다.")
    @PostMapping("/{cleaningId}/verify")
    public ApiResponse<CleaningVerifyResponse> verifyCleaning(
            @LoginUserId Long userId,
            @PathVariable Long cleaningId,
            @Valid @RequestBody CleaningVerifyRequest request
    ) {
        CleaningVerifyResponse response = cleaningService.verifyCleaning(userId, cleaningId, request);

        return ApiResponse.ok(response);
    }
}
