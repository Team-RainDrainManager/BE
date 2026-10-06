package com.rainbutler.global.exception;

import com.rainbutler.global.response.ApiResponse;
import com.rainbutler.global.response.ErrorResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 전역 예외 처리기입니다. 모든 예외를 {@link ApiResponse} 실패 형식으로 바꿉니다.
 *
 * <p>로그: 4xx는 warn으로 코드와 메시지만, 5xx는 error로 스택 트레이스까지 남깁니다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 서비스에서 던진 비즈니스 예외를 처리합니다.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        return toResponse(e.getErrorCode(), ErrorResponse.of(e.getErrorCode(), e.getMessage()), e);
    }

    /**
     * {@code @Valid @RequestBody} 검증 실패를 처리합니다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e) {
        List<ErrorResponse.FieldError> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorResponse.FieldError(
                        error.getField(), error.getDefaultMessage()))
                .toList();
        return toResponse(ErrorCode.INVALID_INPUT,
                ErrorResponse.of(ErrorCode.INVALID_INPUT, fieldErrors), e);
    }

    /**
     * 컨트롤러 메서드 파라미터(@RequestParam, @PathVariable 등) 검증 실패를 처리합니다.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleHandlerMethodValidation(
            HandlerMethodValidationException e) {
        List<ErrorResponse.FieldError> fieldErrors = new ArrayList<>();
        e.getParameterValidationResults().forEach(result -> {
            String parameterName = result.getMethodParameter().getParameterName();
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                // 객체 파라미터의 필드 오류면 필드명을, 단일 파라미터면 파라미터명을 쓴다
                String field = error instanceof FieldError fieldError
                        ? fieldError.getField() : parameterName;
                fieldErrors.add(new ErrorResponse.FieldError(field, error.getDefaultMessage()));
            }
        });
        return toResponse(ErrorCode.INVALID_INPUT,
                ErrorResponse.of(ErrorCode.INVALID_INPUT, fieldErrors), e);
    }

    /**
     * 서비스 계층 {@code @Validated} 검증 실패를 처리합니다.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException e) {
        List<ErrorResponse.FieldError> fieldErrors = e.getConstraintViolations().stream()
                .map(violation -> new ErrorResponse.FieldError(
                        lastNodeName(violation), violation.getMessage()))
                .toList();
        return toResponse(ErrorCode.INVALID_INPUT,
                ErrorResponse.of(ErrorCode.INVALID_INPUT, fieldErrors), e);
    }

    /**
     * 요청 본문 JSON 형식 오류를 처리합니다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        return toResponse(ErrorCode.INVALID_INPUT,
                ErrorResponse.of(ErrorCode.INVALID_INPUT, "요청 본문 형식이 올바르지 않아요."), e);
    }

    /**
     * 파라미터 타입 변환 실패를 처리합니다. (예: 숫자 자리에 문자)
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException e) {
        return toResponse(ErrorCode.INVALID_TYPE, e);
    }

    /**
     * 필수 요청 파라미터 누락을 처리합니다.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(
            MissingServletRequestParameterException e) {
        return toResponse(ErrorCode.MISSING_PARAMETER, e);
    }

    /**
     * 지원하지 않는 HTTP 메서드 요청을 처리합니다.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException e) {
        return toResponse(ErrorCode.METHOD_NOT_ALLOWED, e);
    }

    /**
     * 존재하지 않는 경로 요청을 처리합니다.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e) {
        return toResponse(ErrorCode.RESOURCE_NOT_FOUND, e);
    }

    /**
     * DB 제약 조건 위반을 처리합니다. 부분 유니크 인덱스 등 동시 요청 충돌을 대비합니다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(
            DataIntegrityViolationException e) {
        return toResponse(ErrorCode.DUPLICATE_RESOURCE, e);
    }

    /**
     * 처리하지 못한 모든 예외를 처리합니다. 상세 원인은 응답에 넣지 않습니다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        return toResponse(ErrorCode.INTERNAL_ERROR, e);
    }

    /**
     * ErrorCode의 기본 메시지로 실패 응답을 만듭니다.
     */
    private ResponseEntity<ApiResponse<Void>> toResponse(ErrorCode errorCode, Exception e) {
        return toResponse(errorCode, ErrorResponse.of(errorCode), e);
    }

    /**
     * 로그를 남기고 실패 응답을 만듭니다.
     */
    private ResponseEntity<ApiResponse<Void>> toResponse(
            ErrorCode errorCode, ErrorResponse errorResponse, Exception e) {
        if (errorCode.getStatus().is5xxServerError()) {
            log.error("[{}] {}", errorCode.name(), e.getMessage(), e);
        } else {
            log.warn("[{}] {}", errorCode.name(), e.getMessage());
        }
        return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.fail(errorResponse));
    }

    /**
     * ConstraintViolation 경로의 마지막 이름을 꺼냅니다. (예: "register.request.nickname" → "nickname")
     */
    private String lastNodeName(ConstraintViolation<?> violation) {
        String name = null;
        for (var node : violation.getPropertyPath()) {
            name = node.getName();
        }
        return name;
    }
}
