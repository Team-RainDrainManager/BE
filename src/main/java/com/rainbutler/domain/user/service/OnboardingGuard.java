package com.rainbutler.domain.user.service;

import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 온보딩(약관 동의 + 본인인증)을 마친 사용자만 통과시키는 가드입니다.
 *
 * <p>공공재를 직접 관리하는 기능의 서비스 첫 줄에서 부릅니다. (입양 청소 시작 · 입양 등록 · 점검 제출 ·
 * 대신 점검 수락 · 점검 쉬기 · 활동 확인서)
 * <pre>
 * onboardingGuard.require(userId);
 * </pre>
 * 이미 User 엔티티를 가지고 있으면 {@code user.requireOnboardingCompleted()}를 바로 불러도 됩니다.
 */
@Component
@RequiredArgsConstructor
public class OnboardingGuard {

    private final UserRepository userRepository;

    /**
     * 온보딩을 마치지 않았으면 막습니다.
     *
     * @param userId 로그인 사용자 id
     * @throws BusinessException 사용자 없음 (USER_NOT_FOUND) · 온보딩 미완료 (ONBOARDING_REQUIRED 403)
     */
    @Transactional(readOnly = true)
    public void require(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND))
                .requireOnboardingCompleted();
    }
}
