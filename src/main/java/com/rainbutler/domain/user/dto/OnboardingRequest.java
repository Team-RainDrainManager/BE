package com.rainbutler.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 온보딩 완료 요청입니다.
 *
 * @param agreements 현재 약관 전부에 대한 동의 여부 (선택 약관 미동의도 false로 보낸다)
 * @param identityVerified 본인인증 진행 여부 (true여야 완료된다)
 */
public record OnboardingRequest(
        @NotEmpty List<@Valid @NotNull Agreement> agreements,
        @Schema(example = "true") Boolean identityVerified) {

    /**
     * 약관 하나에 대한 동의 여부입니다.
     *
     * @param termsId 약관 id (GET /api/v1/terms의 termsId)
     * @param agreed 동의 여부
     */
    public record Agreement(
            @Schema(example = "1") @NotNull Long termsId,
            @Schema(example = "true") @NotNull Boolean agreed) {
    }
}
