package com.rainbutler.domain.user.dto;

import com.rainbutler.domain.user.validation.NicknamePolicy;
import com.rainbutler.domain.user.validation.ValidNickname;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 닉네임 수정 요청입니다. 가입과 같은 규칙(앞뒤 공백 제거 후 2~20자)을 씁니다.
 *
 * @param nickname 새 닉네임
 */
public record NicknameUpdateRequest(
        @Schema(example = "또랑이집사") @ValidNickname String nickname) {

    /** 검증 전에 앞뒤 공백을 지웁니다. */
    public NicknameUpdateRequest {
        nickname = NicknamePolicy.normalize(nickname);
    }
}
