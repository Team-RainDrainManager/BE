package com.rainbutler.domain.user.service;

import com.rainbutler.domain.user.dto.MeResponse;
import com.rainbutler.domain.user.dto.NicknameUpdateRequest;
import com.rainbutler.domain.user.dto.NicknameUpdateResponse;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 정보를 조회합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    /**
     * 로그인 사용자의 정보를 조회합니다.
     *
     * @param userId 로그인 사용자 id
     * @return 내 정보
     * @throws BusinessException 사용자가 없을 때 (USER_NOT_FOUND)
     */
    public MeResponse getMe(Long userId) {
        return userRepository.findById(userId)
                .map(MeResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * 로그인 사용자의 닉네임을 바꿉니다. 중복을 허용하고, 같은 닉네임으로 바꿔도 성공합니다.
     *
     * @param userId 로그인 사용자 id
     * @param request 새 닉네임 (검증 · 앞뒤 공백 제거 완료)
     * @return 바뀐 닉네임과 수정 시각
     * @throws BusinessException 사용자가 없을 때 (USER_NOT_FOUND)
     */
    @Transactional
    public NicknameUpdateResponse updateNickname(Long userId, NicknameUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.changeNickname(request.nickname());
        // 수정 시각(Auditing)이 응답에 담기도록 먼저 반영한다
        userRepository.flush();
        return NicknameUpdateResponse.from(user);
    }
}
