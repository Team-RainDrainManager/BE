package com.rainbutler.domain.user.identity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 시연용 본인인증 목업입니다. 개인정보를 받지 않고 바로 성공 처리합니다.
 */
@Slf4j
@Component
public class MockIdentityVerifier implements IdentityVerifier {

    @Override
    public boolean verify(Long userId) {
        log.info("[본인인증 목업] userId={} 개인정보 없이 인증 완료 처리", userId);
        return true;
    }
}
