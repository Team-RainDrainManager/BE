package com.rainbutler.domain.user.controller;

import com.rainbutler.domain.user.certificate.CertificateService;
import com.rainbutler.domain.user.certificate.IssuedCertificate;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.security.LoginUserId;
import com.rainbutler.global.swagger.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 활동 확인서 API입니다.
 */
@Tag(name = "User", description = "내 정보")
@RestController
@RequestMapping("/api/v1/users/me/certificate")
@RequiredArgsConstructor
public class CertificateController {

    private final CertificateService certificateService;

    /**
     * 활동 확인서를 PDF로 내려줍니다.
     */
    @Operation(summary = "활동 확인서 PDF 다운로드",
            description = "실명을 등록하고 인정된 점검 기록이 1건 이상 있어야 합니다. 서버에 저장하지 않고 "
                    + "요청할 때마다 A4 한 장 PDF를 만들어 바로 다운로드합니다. "
                    + "1365 자원봉사포털 등록 전에는 공식 봉사 실적이 아닙니다.")
    @ApiErrorCodes({ErrorCode.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN, ErrorCode.INVALID_TOKEN,
            ErrorCode.USER_NOT_FOUND, ErrorCode.IDENTITY_VERIFICATION_REQUIRED,
            ErrorCode.CERTIFICATE_NO_ACTIVITY})
    @GetMapping(produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> download(@LoginUserId Long userId) {
        IssuedCertificate certificate = certificateService.issue(userId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + certificate.filename() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .contentLength(certificate.content().length)
                .body(certificate.content());
    }
}
