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

    @Operation(summary = "청소 완료 인증", description = "청소 완료 후 AI 인증을 진행합니다. afterGrade < beforeGrade이면 통과하며, 통과해야 입양 가능합니다.")
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
