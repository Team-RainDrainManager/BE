package com.rainbutler.domain.adoption.controller;

import com.rainbutler.domain.adoption.dto.Response.AdoptionNicknameCheckResponse;
import com.rainbutler.domain.adoption.service.AdoptionService;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.response.ApiResponse;
import com.rainbutler.global.swagger.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 입양할 빗물받이의 별명 중복을 확인하는 API입니다. */
@Tag(name = "Adoption", description = "입양")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/adoptions/nickname")
public class AdoptionNicknameCheckController {

    private final AdoptionService adoptionService;

    @Operation(summary = "입양 빗물받이 별명 중복 확인",
            description = "입양 등록 · 별명 변경 전에 쓸 수 있는 별명인지 확인합니다. "
                    + "입양 중(ACTIVE)인 모든 빗물받이의 별명과 대소문자 구분 없이 비교하며, "
                    + "부르는 쪽이 없어도 등록 · 변경 시 서버가 한 번 더 확인합니다. "
                    + "별명 규칙은 입양 등록과 같습니다. (1~20자) 로그인이 필요합니다.")
    @ApiErrorCodes({ErrorCode.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN, ErrorCode.INVALID_TOKEN,
            ErrorCode.INVALID_INPUT})
    @GetMapping("/check")
    public ResponseEntity<ApiResponse<AdoptionNicknameCheckResponse>> checkNickname(
            @Parameter(description = "확인할 별명 (앞뒤 공백 제외 1~20자)", example = "또랑이")
            @RequestParam
            @NotBlank(message = "별명을 입력해 주세요.")
            @Size(min = 1, max = 20, message = "별명은 1~20자로 입력해 주세요.")
            String nickname,

            @Parameter(description = "별명을 바꾸는 입양 id. 자기 자신은 중복에서 뺀다")
            @RequestParam(required = false)
            Long excludeAdoptionId) {
        return ResponseEntity.ok(ApiResponse.ok(
                adoptionService.checkNickname(nickname, excludeAdoptionId)));
    }
}
