package com.rainbutler.global.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 로그인 사용자 id(access 토큰의 {@code sub})를 컨트롤러 파라미터로 받습니다.
 *
 * <pre>
 * public ResponseEntity&lt;ApiResponse&lt;MeResponse&gt;&gt; me(@LoginUserId Long userId)
 * </pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LoginUserId {
}
