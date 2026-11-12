package com.rainbutler.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 프로필 사진 변경 요청입니다.
 *
 * <p>{@code POST /api/v1/uploads/presigned-url}(purpose=PROFILE)로 사진을 올린 뒤 받은 key를
 * 보냅니다.
 *
 * @param photoKey 업로드한 프로필 사진 key
 */
public record ProfileImageUpdateRequest(
        @Schema(example = "prod/profile/12/2f1c....jpg")
        @NotBlank(message = "프로필 사진을 등록해 주세요.")
        @Size(max = 500)
        String photoKey) {
}
