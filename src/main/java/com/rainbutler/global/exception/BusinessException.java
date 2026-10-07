package com.rainbutler.global.exception;

import lombok.Getter;

/**
 * 비즈니스 규칙 위반 예외입니다. GlobalExceptionHandler가 ErrorCode에 맞는 응답으로 바꿉니다.
 *
 * <pre>
 * throw new BusinessException(ErrorCode.DRAIN_NOT_FOUND);
 * </pre>
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * ErrorCode의 기본 메시지로 예외를 만듭니다.
     *
     * @param errorCode 에러 코드
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /**
     * 메시지를 덮어써서 예외를 만듭니다. 응답 메시지도 이 값으로 나갑니다.
     *
     * @param errorCode 에러 코드
     * @param message 응답 메시지
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
