package com.rainbutler.global.response;

import com.rainbutler.global.exception.ErrorCode;
import java.util.List;

/**
 * 실패 응답의 에러 정보입니다.
 *
 * @param code 에러 코드 (ErrorCode 이름)
 * @param message 사용자에게 보여줄 메시지
 * @param fieldErrors 입력값 검증 실패 목록. 검증 실패가 아니면 빈 리스트
 */
public record ErrorResponse(String code, String message, List<FieldError> fieldErrors) {

    /**
     * ErrorCode의 기본 메시지로 에러 정보를 만듭니다.
     *
     * @param errorCode 에러 코드
     * @return 에러 정보
     */
    public static ErrorResponse of(ErrorCode errorCode) {
        return of(errorCode, errorCode.getMessage());
    }

    /**
     * 메시지를 덮어써서 에러 정보를 만듭니다.
     *
     * @param errorCode 에러 코드
     * @param message 응답 메시지
     * @return 에러 정보
     */
    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.name(), message, List.of());
    }

    /**
     * 입력값 검증 실패 목록을 담아 에러 정보를 만듭니다.
     *
     * @param errorCode 에러 코드
     * @param fieldErrors 검증 실패 목록
     * @return 에러 정보
     */
    public static ErrorResponse of(ErrorCode errorCode, List<FieldError> fieldErrors) {
        return new ErrorResponse(
                errorCode.name(), errorCode.getMessage(), List.copyOf(fieldErrors));
    }

    /**
     * 입력값 검증 실패 항목입니다.
     *
     * @param field 실패한 필드(파라미터) 이름
     * @param reason 실패 사유
     */
    public record FieldError(String field, String reason) {
    }
}
