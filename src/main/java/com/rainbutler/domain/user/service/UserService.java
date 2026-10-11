package com.rainbutler.domain.user.service;

import com.rainbutler.domain.user.dto.MeResponse;
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
}
