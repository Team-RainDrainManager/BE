package com.rainbutler.global.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 받침에 맞는 조사를 붙입니다. (또랑이 이름처럼 사용자가 지은 이름 뒤)
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class KoreanJosa {

    private static final char HANGUL_START = '가';
    private static final char HANGUL_END = '힣';
    private static final int JONGSEONG_COUNT = 28;

    /**
     * 을 / 를을 붙입니다. 한글이 아닌 글자로 끝나면 "를"을 붙입니다.
     *
     * @param word 이름
     * @return 예: 또랑이 → 또랑이를, 빗물몽 → 빗물몽을
     */
    public static String eulReul(String word) {
        return word + (hasJongseong(word) ? "을" : "를");
    }

    private static boolean hasJongseong(String word) {
        if (word == null || word.isEmpty()) {
            return false;
        }
        char last = word.charAt(word.length() - 1);
        if (last < HANGUL_START || last > HANGUL_END) {
            return false;
        }
        return (last - HANGUL_START) % JONGSEONG_COUNT != 0;
    }
}
