package com.rainbutler.domain.auth.service;

import com.rainbutler.domain.auth.client.KakaoOAuthClient;
import com.rainbutler.domain.auth.client.KakaoUserInfo;
import com.rainbutler.domain.auth.dto.KakaoLoginResponse;
import com.rainbutler.domain.user.entity.Provider;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 카카오 로그인: 인가 코드 → 카카오 사용자 조회 → 회원 조회 · 가입 → 우리 JWT 발급.
 *
 * <p>카카오 API 호출 동안 DB 트랜잭션을 잡지 않도록 이 메서드에는 트랜잭션을 걸지 않습니다.
 */
@Service
@RequiredArgsConstructor
public class KakaoAuthService {

    private final KakaoOAuthClient kakaoOAuthClient;
    private final UserRepository userRepository;
    private final TokenService tokenService;

    /**
     * 카카오 인가 코드로 로그인합니다. 처음이면 가입합니다.
     *
     * @param code 카카오 인가 코드
     * @param redirectUri 인가 요청 때 쓴 redirectUri
     * @return 토큰 + 신규 가입 여부
     */
    public KakaoLoginResponse login(String code, String redirectUri) {
        KakaoUserInfo kakaoUser = kakaoOAuthClient.fetchUser(code, redirectUri);

        User user = userRepository.findByProviderAndProviderId(Provider.KAKAO, kakaoUser.kakaoId())
                .orElse(null);
        boolean isNewUser = user == null;
        if (isNewUser) {
            user = register(kakaoUser);
        }
        return KakaoLoginResponse.of(tokenService.issue(user), isNewUser);
    }

    /**
     * 카카오 사용자를 가입시킵니다. 동시 요청으로 이미 가입됐으면 그 사용자를 씁니다.
     */
    private User register(KakaoUserInfo kakaoUser) {
        try {
            return userRepository.saveAndFlush(User.kakao(
                    kakaoUser.kakaoId(), kakaoUser.nickname(), kakaoUser.profileImageUrl()));
        } catch (DataIntegrityViolationException e) {
            return userRepository.findByProviderAndProviderId(Provider.KAKAO, kakaoUser.kakaoId())
                    .orElseThrow(() -> e);
        }
    }
}
