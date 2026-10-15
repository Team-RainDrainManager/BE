package com.rainbutler.domain.push.service;

import com.rainbutler.domain.push.entity.FcmPlatform;
import com.rainbutler.domain.push.entity.FcmToken;
import com.rainbutler.domain.push.repository.FcmTokenRepository;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FCM 기기 토큰을 등록 · 삭제합니다.
 */
@Service
@RequiredArgsConstructor
public class FcmTokenService {

    private final FcmTokenRepository fcmTokenRepository;
    private final UserRepository userRepository;

    /**
     * 토큰을 등록하거나 갱신합니다.
     *
     * <p>같은 토큰이 이미 있으면 현재 사용자로 옮기고 등록 시각을 갱신합니다. (같은 기기에서 계정을 바꾼 경우)
     *
     * @param userId 로그인 사용자 id
     * @param token FCM 등록 토큰
     * @param platform 기기 종류
     */
    @Transactional
    public void register(Long userId, String token, FcmPlatform platform) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        fcmTokenRepository.findByToken(token).ifPresentOrElse(
                existing -> existing.refresh(user, platform),
                () -> fcmTokenRepository.save(FcmToken.builder()
                        .user(user)
                        .token(token)
                        .platform(platform)
                        .build()));
    }

    /**
     * 토큰을 하드 삭제합니다. (로그아웃 전에 프론트가 호출)
     *
     * <p>없거나 다른 사용자의 토큰이면 아무것도 하지 않습니다. (항상 성공 응답)
     *
     * @param userId 로그인 사용자 id
     * @param token 삭제할 FCM 등록 토큰
     */
    @Transactional
    public void delete(Long userId, String token) {
        fcmTokenRepository.findByToken(token)
                .filter(existing -> existing.getUser().getId().equals(userId))
                .ifPresent(fcmTokenRepository::delete);
    }
}
