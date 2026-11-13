package com.rainbutler.domain.adoption.dto.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 입양 빗물받이 별명 변경 요청입니다.
 *
 * <p>별명 규칙은 입양 등록과 같습니다. (1~20자)
 *
 * @param nickname 새 별명
 */
public record AdoptionNicknameUpdateRequest(
        @NotBlank(message = "별명을 입력해 주세요.")
        @Size(min = 1, max = 20, message = "별명은 1~20자로 입력해 주세요.")
        String nickname
) {
}
