package com.rainbutler.domain.user.dto;

import com.rainbutler.domain.user.entity.User;
import java.time.LocalDateTime;

/**
 * 프로필 사진 변경 응답입니다.
 *
 * @param id 사용자 id
 * @param profileImage 프로필 사진 조회 URL (삭제했으면 null)
 * @param updatedAt 수정 시각
 */
public record ProfileImageUpdateResponse(Long id, String profileImage, LocalDateTime updatedAt) {

    /**
     * 사용자 엔티티와 조회 URL로 응답을 만듭니다.
     *
     * @param user 사용자
     * @param profileImage 프로필 사진 조회 URL (없으면 null)
     * @return 응답
     */
    public static ProfileImageUpdateResponse of(User user, String profileImage) {
        return new ProfileImageUpdateResponse(user.getId(), profileImage, user.getUpdatedAt());
    }
}
