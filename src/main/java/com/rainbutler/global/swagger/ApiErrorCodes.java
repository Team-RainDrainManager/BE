package com.rainbutler.global.swagger;

import com.rainbutler.global.exception.ErrorCode;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * API에서 날 수 있는 에러 코드를 적으면 Swagger에 HTTP 상태별 실패 응답 예시가 추가됩니다.
 *
 * <pre>
 * &#64;ApiErrorCodes({ErrorCode.INVALID_INPUT, ErrorCode.EMAIL_ALREADY_EXISTS})
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiErrorCodes {

    /**
     * 이 API에서 날 수 있는 에러 코드 목록입니다.
     */
    ErrorCode[] value();
}
