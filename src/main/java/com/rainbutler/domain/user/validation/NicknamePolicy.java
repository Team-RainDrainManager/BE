package com.rainbutler.domain.user.validation;

/**
 * 닉네임 규칙입니다. 가입과 닉네임 수정이 같은 규칙을 씁니다. (앞뒤 공백 제거 후 2~20자)
 */
public final class NicknamePolicy {

    /** 최소 길이 */
    public static final int MIN_LENGTH = 2;

    /** 최대 길이 */
    public static final int MAX_LENGTH = 20;

    private NicknamePolicy() {
    }

    /**
     * 검증 전에 앞뒤 공백을 지웁니다.
     *
     * @param nickname 입력 닉네임
     * @return 앞뒤 공백을 지운 닉네임 (null이면 null)
     */
    public static String normalize(String nickname) {
        return nickname == null ? null : nickname.strip();
    }
}
