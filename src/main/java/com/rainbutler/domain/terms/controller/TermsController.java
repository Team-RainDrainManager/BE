package com.rainbutler.domain.terms.controller;

import com.rainbutler.domain.terms.dto.TermsContentResponse;
import com.rainbutler.domain.terms.dto.TermsResponse;
import com.rainbutler.domain.terms.service.TermsService;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.response.ApiResponse;
import com.rainbutler.global.swagger.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 약관 조회 API입니다. 로그인 없이 볼 수 있습니다.
 */
@Tag(name = "Terms", description = "약관 목록 · 전문")
@SecurityRequirements
@RestController
@RequestMapping("/api/v1/terms")
@RequiredArgsConstructor
public class TermsController {

    private static final String LIST_DESCRIPTION = """
            현재 약관 목록입니다. (type별 최신 버전, `displayOrder` 순)
            필수 · 선택은 `required`로 구분하고, 체크박스 앞의 [필수] · [선택]은 프론트가 붙입니다.

            **화면 문구**
            - 맨 위 전체 동의: "약관 전체 동의하기 (선택 항목 포함)"
            - 전체 동의 아래: "선택 항목에 동의하지 않아도 서비스를 이용할 수 있어요. 알림 설정은 언제든 [내 정보 > 설정]에서 바꿀 수 있어요."
            """;

    private final TermsService termsService;

    /**
     * 현재 약관 목록을 조회합니다.
     */
    @Operation(summary = "약관 목록 조회", description = LIST_DESCRIPTION)
    @GetMapping
    public ResponseEntity<ApiResponse<List<TermsResponse>>> getTerms() {
        return ResponseEntity.ok(ApiResponse.ok(termsService.getCurrentTerms()));
    }

    /**
     * 약관 전문(markdown)을 조회합니다.
     */
    @Operation(summary = "약관 전문 조회",
            description = "`content`는 markdown 문자열입니다. 전문이 없는 약관(만 14세 이상 확인)은 null입니다.")
    @ApiErrorCodes({ErrorCode.TERMS_NOT_FOUND, ErrorCode.INVALID_TYPE})
    @GetMapping("/{termsId}")
    public ResponseEntity<ApiResponse<TermsContentResponse>> getContent(
            @PathVariable Long termsId) {
        return ResponseEntity.ok(ApiResponse.ok(termsService.getContent(termsId)));
    }
}
