package com.rainbutler.domain.user.dto;

import java.time.LocalDateTime;

/**
 * 실명 등록 결과입니다.
 *
 * @param realName 저장된 실명
 * @param method 인증 방식 (지금은 {@value #METHOD_MOCK})
 * @param verifiedAt 인증 시각
 */
public record IdentityVerifyResponse(
        String realName,
        String method,
        LocalDateTime verifiedAt) {

    /** 본인인증 대행사 연동 전 목업 방식 */
    public static final String METHOD_MOCK = "MOCK";
}
