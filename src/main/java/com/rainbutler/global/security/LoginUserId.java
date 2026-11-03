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
 *
 * <p>로그인 전에도 열리는 조회에서 "내 것인지"만 선택적으로 확인하려면 {@code @LoginUserId(required =
 * false)}를 씁니다. 이때 비로그인이면 null이 들어갑니다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LoginUserId {

    /** 로그인 필수 여부. false면 비로그인 시 null을 넣는다. */
    boolean required() default true;
}
