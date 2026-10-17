package com.rainbutler.domain.user.identity;

/**
 * 본인인증 수단입니다. 실제 본인인증을 붙일 때 구현만 교체합니다.
 */
public interface IdentityVerifier {

    /**
     * 사용자의 본인인증을 진행합니다.
     *
     * @param userId 사용자 id
     * @return 인증 성공 여부
     */
    boolean verify(Long userId);
}
