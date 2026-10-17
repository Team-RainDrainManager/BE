package com.rainbutler.domain.user.dto;

import com.rainbutler.domain.user.entity.User;
import java.time.LocalDateTime;

/**
 * 닉네임 수정 응답입니다.
 *
 * @param id 사용자 id
 * @param nickname 바뀐 닉네임
 * @param updatedAt 수정 시각
 */
public record NicknameUpdateResponse(Long id, String nickname, LocalDateTime updatedAt) {

    /**
     * 사용자 엔티티로 응답을 만듭니다.
     */
    public static NicknameUpdateResponse from(User user) {
        return new NicknameUpdateResponse(user.getId(), user.getNickname(), user.getUpdatedAt());
    }
}
