package com.rainbutler.domain.drain.controller;

import com.rainbutler.domain.drain.dto.Response.GrowthResponse;
import com.rainbutler.domain.drain.service.DrainService;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.response.ApiResponse;
import com.rainbutler.global.swagger.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Drain", description = "빗물받이")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/drains/{drainId}/growth")
public class DrainGrowthController {

    private final DrainService drainService;

    @Operation(summary = "성장 조회", description = "빗물받이 입양 귀속의 레벨·점수·다음 레벨 진행률을 조회합니다. 로그인이 필요합니다.")
    @ApiErrorCodes({ErrorCode.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN, ErrorCode.INVALID_TOKEN,
            ErrorCode.DRAIN_NOT_FOUND, ErrorCode.ADOPTION_NOT_FOUND})
    @GetMapping
    public ApiResponse<GrowthResponse> getGrowth(
            @PathVariable Long drainId
    ) {
        GrowthResponse response = drainService.getGrowth(drainId);

        return ApiResponse.ok(response);
    }
}
