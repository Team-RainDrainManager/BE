package com.rainbutler.domain.push.dto;

import com.rainbutler.domain.push.entity.FcmPlatform;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * FCM 토큰 등록 · 갱신 요청입니다.
 *
 * @param token FCM 등록 토큰 (4096자 이하, 공백 불가)
 * @param platform 기기 종류 (WEB · ANDROID · IOS)
 */
public record FcmTokenRegisterRequest(
        @Schema(example = "fcm-registration-token")
        @NotBlank @Size(max = 4096) @Pattern(regexp = "^\\S+$", message = "공백을 포함할 수 없습니다")
        String token,

        @Schema(example = "WEB") @NotNull
        FcmPlatform platform) {
}
