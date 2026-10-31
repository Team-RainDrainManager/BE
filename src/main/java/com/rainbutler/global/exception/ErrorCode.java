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
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "로그인이 만료됐어요. 토큰을 재발급해 주세요."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "인증 정보가 올바르지 않아요. 다시 로그인해 주세요."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없어요."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않아요."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입된 이메일이에요."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "로그인 정보가 만료됐어요. 다시 로그인해 주세요."),
    INVALID_REDIRECT_URI(HttpStatus.BAD_REQUEST, "허용되지 않은 리다이렉트 주소예요."),
    KAKAO_AUTH_FAILED(HttpStatus.UNAUTHORIZED, "카카오 로그인에 실패했어요. 다시 시도해 주세요."),
    KAKAO_API_ERROR(HttpStatus.BAD_GATEWAY, "카카오 서버와 통신하지 못했어요. 잠시 후 다시 시도해 주세요."),

    // ───────── 약관 · 온보딩 ─────────
    TERMS_NOT_FOUND(HttpStatus.NOT_FOUND, "약관을 찾을 수 없어요."),
    ONBOARDING_REQUIRED(HttpStatus.FORBIDDEN, "약관 동의와 본인인증을 먼저 완료해 주세요."),
    REQUIRED_TERMS_NOT_AGREED(HttpStatus.BAD_REQUEST, "필수 약관에 모두 동의해 주세요."),
    TERMS_OUTDATED(HttpStatus.BAD_REQUEST, "약관이 변경됐어요. 다시 확인해 주세요."),
    IDENTITY_VERIFICATION_REQUIRED_ON_ONBOARDING(HttpStatus.BAD_REQUEST, "본인인증을 진행해 주세요."),
    ONBOARDING_ALREADY_COMPLETED(HttpStatus.CONFLICT, "이미 온보딩을 마쳤어요. 알림 설정은 설정에서 바꿀 수 있어요."),

    // ───────── 사용자 ─────────
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없어요."),
    USER_SANCTIONED(HttpStatus.FORBIDDEN, "제재 기간에는 이용할 수 없어요."),
    PAUSE_ALREADY_ACTIVE(HttpStatus.CONFLICT, "이미 점검 쉬기 중이에요."),
    PAUSE_NO_ADOPTION(HttpStatus.CONFLICT, "입양한 빗물받이가 있어야 쉬기를 신청할 수 있어요."),
    PAUSE_COOLDOWN(HttpStatus.CONFLICT, "점검 쉬기는 90일에 한 번 신청할 수 있어요."),
    WITHDRAW_TOOL_NOT_RETURNED(HttpStatus.CONFLICT, "빌린 도구를 반납한 뒤 탈퇴할 수 있어요."),

    // ───────── 빗물받이 ─────────
    DRAIN_NOT_FOUND(HttpStatus.NOT_FOUND, "빗물받이를 찾을 수 없어요."),

    // ───────── 입양 ─────────
    ADOPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "입양 정보를 찾을 수 없어요."),
    ADOPTION_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "빗물받이는 최대 3개까지 입양할 수 있어요."),
    DRAIN_ALREADY_ADOPTED(HttpStatus.CONFLICT, "이미 다른 집사가 입양한 빗물받이예요."),

    // ───────── 사진 ─────────
    DUPLICATE_PHOTO(HttpStatus.CONFLICT, "이미 등록된 사진이에요. 새로 촬영한 사진을 올려 주세요."),
    AI_API_ERROR(HttpStatus.BAD_GATEWAY, "AI 판정에 실패했어요. 잠시 후 다시 시도해 주세요."),
    UNSUPPORTED_IMAGE_TYPE(HttpStatus.BAD_REQUEST, "JPG, PNG, WEBP 사진만 올릴 수 있어요."),
    INVALID_PHOTO_KEY(HttpStatus.BAD_REQUEST, "사진 정보가 올바르지 않아요. 다시 업로드해 주세요."),
    PHOTO_NOT_UPLOADED(HttpStatus.BAD_REQUEST, "사진 업로드가 끝나지 않았어요. 다시 업로드해 주세요."),
    PHOTO_TOO_LARGE(HttpStatus.BAD_REQUEST, "사진은 10MB 이하만 올릴 수 있어요."),
    PHOTO_FORBIDDEN(HttpStatus.FORBIDDEN, "직접 올린 사진만 사용할 수 있어요."),
    STORAGE_ERROR(HttpStatus.BAD_GATEWAY, "사진 저장소와 통신하지 못했어요. 잠시 후 다시 시도해 주세요."),
    STORAGE_NOT_CONFIGURED(HttpStatus.INTERNAL_SERVER_ERROR,
            "사진 업로드를 사용할 수 없어요. 관리자에게 문의해 주세요."),

    // ───────── 점검 요청 ─────────
    REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "점검 요청을 찾을 수 없어요."),
    REQUEST_REPLY_NOT_ALLOWED(HttpStatus.CONFLICT, "지금은 응답할 수 없는 요청이에요."),
    REQUEST_NOT_COMPLETABLE(HttpStatus.CONFLICT, "이 요청은 지금 완료 처리할 수 없어요."),
    REQUEST_ALREADY_CLAIMED(HttpStatus.CONFLICT, "이미 다른 분이 대신 점검을 맡았어요."),
    REQUEST_NOT_OPEN(HttpStatus.CONFLICT, "지금은 대신 점검을 신청할 수 없는 요청이에요."),
    SUBSTITUTE_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "대신 점검 가능한 거리를 벗어났어요."),
    SUBSTITUTE_DAILY_LIMIT(HttpStatus.CONFLICT, "오늘 대신 점검 가능한 횟수를 모두 사용했어요."),
    SUBSTITUTE_OWN_REQUEST(HttpStatus.FORBIDDEN,
            "내 또랑이는 대신 점검으로 맡을 수 없어요. 요청에 응답해 주세요."),

    // ───────── 도구함 ─────────
    TOOL_BOX_NOT_FOUND(HttpStatus.NOT_FOUND, "도구함을 찾을 수 없어요."),
    TOOL_BOX_EMPTY(HttpStatus.CONFLICT, "도구함에 남은 도구가 없어요."),
    TOOL_ALREADY_RENTED(HttpStatus.CONFLICT, "이미 대여 중인 도구가 있어요. 반납 후 다시 빌려 주세요."),
    TOOL_RETURN_MISMATCH(HttpStatus.BAD_REQUEST, "빌린 도구함에만 반납할 수 있어요."),

    // ───────── 제보 ─────────
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "제보를 찾을 수 없어요."),

    //───────── 위치 ─────────
    LOCATION_INVALID_INPUT(HttpStatus.BAD_REQUEST, "위치 정보가 올바르지 않아요");

    private final HttpStatus status;
    private final String message;
}
