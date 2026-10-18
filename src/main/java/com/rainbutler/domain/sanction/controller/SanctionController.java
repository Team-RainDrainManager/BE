package com.rainbutler.domain.sanction.controller;

import com.rainbutler.domain.sanction.dto.SanctionResponse;
import com.rainbutler.domain.sanction.service.SanctionService;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.response.ApiResponse;
import com.rainbutler.global.security.LoginUserId;
import com.rainbutler.global.swagger.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 제재 내역 API입니다.
 */
@Tag(name = "User", description = "내 정보")
@RestController
@RequestMapping("/api/v1/users/me/sanctions")
@RequiredArgsConstructor
public class SanctionController {

    private final SanctionService sanctionService;

    /**
     * 내 제재 내역을 조회합니다.
     */
    @Operation(summary = "내 제재 내역 조회",
            description = "최신순. 해제된 제재도 포함합니다 (`active=false`, `revokedAt` 있음). "
                    + "허위 제보 제재는 1회 7일 · 2회 14일 · 3회 이상 영구이고, 영구면 `until`이 null입니다.")
    @ApiErrorCodes({ErrorCode.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN, ErrorCode.INVALID_TOKEN})
    @GetMapping
    public ResponseEntity<ApiResponse<List<SanctionResponse>>> getMySanctions(
            @LoginUserId Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(sanctionService.getMyHistory(userId)));
    }
}
