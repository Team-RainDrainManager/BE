package com.rainbutler.domain.auth.service;

import com.rainbutler.domain.auth.dto.LoginRequest;
import com.rainbutler.domain.auth.dto.SignupRequest;
import com.rainbutler.domain.auth.dto.TokenResponse;
import com.rainbutler.domain.user.entity.Provider;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이메일 · 비밀번호 자체 회원가입과 로그인을 처리합니다.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    /** 존재하지 않는 사용자와 비교할 해시 (응답 시간으로 가입 여부가 드러나지 않게 한다) */
    private final String dummyEncodedPassword;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
            TokenService tokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.dummyEncodedPassword = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * 자체 회원가입 후 바로 토큰을 발급합니다.
     *
     * @param request 가입 요청
     * @return 발급한 토큰
     * @throws BusinessException 이미 가입된 이메일 (EMAIL_ALREADY_EXISTS)
     */
    @Transactional
    public TokenResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByProviderAndEmail(Provider.LOCAL, email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        User user = User.local(email, passwordEncoder.encode(request.password()),
                request.nickname());
        try {
            // 동시 가입으로 부분 유니크 인덱스가 걸리면 같은 에러로 응답한다
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        return tokenService.issue(user);
    }

    /**
     * 자체 로그인. 이메일이 없든 비밀번호가 틀리든 같은 에러로 응답합니다.
     *
     * @param request 로그인 요청
     * @return 발급한 토큰
     * @throws BusinessException 이메일 · 비밀번호 불일치 (INVALID_CREDENTIALS)
     */
    @Transactional
    public TokenResponse login(LoginRequest request) {
        Optional<User> user = userRepository.findByProviderAndEmail(
                Provider.LOCAL, normalizeEmail(request.email()));
        // 사용자가 없어도 비교 연산을 한 번 수행해 응답 시간으로 가입 여부가 드러나지 않게 한다
        String encodedPassword = user.map(User::getPassword).orElse(dummyEncodedPassword);
        boolean matches = passwordEncoder.matches(request.password(), encodedPassword);
        if (user.isEmpty() || !matches) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        return tokenService.issue(user.get());
    }

    /**
     * 이메일을 소문자로 정규화합니다. (DB 유니크 인덱스 lower(email)과 기준을 맞춘다)
     */
    private static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
