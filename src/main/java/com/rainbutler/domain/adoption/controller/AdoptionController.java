package com.rainbutler.domain.adoption.controller;

import com.rainbutler.domain.adoption.dto.Request.AdoptionRegisterRequest;
import com.rainbutler.domain.adoption.dto.Response.AdoptionRegisterResponse;
import com.rainbutler.domain.adoption.service.AdoptionService;
import com.rainbutler.global.response.ApiResponse;
import com.rainbutler.global.security.LoginUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Adoption", description = "입양")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/drains/{drainId}/adoptions")
public class AdoptionController {

    private final AdoptionService adoptionService;

    @Operation(summary = "입양 등록", description = "인증 통과한 청소를 통해 빗물받이를 입양합니다.")
    @PostMapping
    public ApiResponse<AdoptionRegisterResponse> registerAdoption(
            @LoginUserId Long userId,
            @PathVariable Long drainId,
            @Valid @RequestBody AdoptionRegisterRequest request
    ) {
        AdoptionRegisterResponse response = adoptionService.registerAdoption(userId, drainId, request);

        return ApiResponse.ok(response);
    }
}
