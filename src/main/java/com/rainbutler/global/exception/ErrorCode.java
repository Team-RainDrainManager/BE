package com.rainbutler.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * API 에러 코드입니다.
 *
 * <p>응답의 {@code error.code}에는 enum 이름이 그대로 나갑니다. 프론트엔드가 코드로 분기하므로 이름을 함부로
 * 바꾸지 마세요. 메시지는 사용자에게 그대로 보여주는 문구입니다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ───────── 공통 ─────────
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값을 다시 확인해 주세요."),
    INVALID_TYPE(HttpStatus.BAD_REQUEST, "입력값의 형식이 올바르지 않아요."),
    MISSING_PARAMETER(HttpStatus.BAD_REQUEST, "필수 값이 빠져 있어요."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식이에요."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 주소를 찾을 수 없어요."),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "이미 처리된 요청이에요. 잠시 후 다시 확인해 주세요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했어요. 잠시 후 다시 시도해 주세요."),

    // ───────── 인증 ─────────
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요해요."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없어요."),

    // ───────── 사용자 ─────────
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없어요."),

    // ───────── 빗물받이 ─────────
    DRAIN_NOT_FOUND(HttpStatus.NOT_FOUND, "빗물받이를 찾을 수 없어요."),

    // ───────── 입양 ─────────
    ADOPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "입양 정보를 찾을 수 없어요."),
    ADOPTION_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "빗물받이는 최대 3개까지 입양할 수 있어요."),
    DRAIN_ALREADY_ADOPTED(HttpStatus.CONFLICT, "이미 다른 집사가 입양한 빗물받이예요."),

    // ───────── 사진 ─────────
    DUPLICATE_PHOTO(HttpStatus.CONFLICT, "이미 등록된 사진이에요. 새로 촬영한 사진을 올려 주세요."),

    // ───────── 점검 요청 ─────────
    REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "점검 요청을 찾을 수 없어요."),
    REQUEST_ALREADY_CLAIMED(HttpStatus.CONFLICT, "이미 다른 분이 대신 점검을 맡았어요."),
    REQUEST_NOT_OPEN(HttpStatus.CONFLICT, "지금은 대신 점검을 신청할 수 없는 요청이에요."),
    SUBSTITUTE_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "대신 점검 가능한 거리를 벗어났어요."),
    SUBSTITUTE_DAILY_LIMIT(HttpStatus.CONFLICT, "오늘 대신 점검 가능한 횟수를 모두 사용했어요."),

    // ───────── 도구함 ─────────
    TOOL_BOX_NOT_FOUND(HttpStatus.NOT_FOUND, "도구함을 찾을 수 없어요."),
    TOOL_BOX_EMPTY(HttpStatus.CONFLICT, "도구함에 남은 도구가 없어요."),
    TOOL_ALREADY_RENTED(HttpStatus.CONFLICT, "이미 대여 중인 도구가 있어요. 반납 후 다시 빌려 주세요."),
    TOOL_RETURN_MISMATCH(HttpStatus.BAD_REQUEST, "빌린 도구함에만 반납할 수 있어요."),

    // ───────── 제보 ─────────
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "제보를 찾을 수 없어요.");

    private final HttpStatus status;
    private final String message;
}
