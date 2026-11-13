package com.rainbutler.domain.adoption.dto.Response;

import com.rainbutler.domain.adoption.entity.Adoption;
import java.time.LocalDateTime;

/**
 * 입양 빗물받이 별명 변경 응답입니다.
 *
 * @param adoptionId 입양 id
 * @param nickname 바뀐 별명
 * @param updatedAt 수정 시각
 */
public record AdoptionNicknameUpdateResponse(
        Long adoptionId,
        String nickname,
        LocalDateTime updatedAt
) {

    /**
     * 입양 엔티티로 응답을 만듭니다.
     *
     * @param adoption 별명을 바꾼 입양
     * @return 별명 변경 응답
     */
    public static AdoptionNicknameUpdateResponse from(Adoption adoption) {
        return new AdoptionNicknameUpdateResponse(
                adoption.getId(), adoption.getNickname(), adoption.getUpdatedAt());
    }
}
