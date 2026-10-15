package com.rainbutler.domain.push.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * FCM 토큰 삭제 요청입니다.
 *
 * @param token 삭제할 FCM 등록 토큰
 */
public record FcmTokenDeleteRequest(@NotBlank @Size(max = 4096) String token) {
}
