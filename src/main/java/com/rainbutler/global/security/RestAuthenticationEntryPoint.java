package com.rainbutler.global.security;

import com.rainbutler.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * 인증 실패(401)를 공통 응답 형식으로 돌려줍니다.
 *
 * <ul>
 *   <li>토큰 없음 → {@code UNAUTHORIZED}</li>
 *   <li>access 만료 → {@code EXPIRED_TOKEN} (프론트가 refresh 호출)</li>
 *   <li>서명 · 형식 · issuer 오류 → {@code INVALID_TOKEN}</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    /** JwtTimestampValidator의 만료 오류 설명 접두어 */
    private static final String EXPIRED_DESCRIPTION_PREFIX = "Jwt expired";

    private final SecurityErrorResponder responder;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        ErrorCode errorCode = resolve(authException);
        log.warn("[{}] {} {}",
                errorCode.name(), request.getRequestURI(), authException.getMessage());
        responder.write(response, errorCode);
    }

    /**
     * 인증 예외를 에러 코드로 바꿉니다.
     */
    static ErrorCode resolve(AuthenticationException e) {
        // Bearer 토큰이 있었는데 검증에 실패한 경우만 OAuth2AuthenticationException이다
        if (!(e instanceof OAuth2AuthenticationException)) {
            return ErrorCode.UNAUTHORIZED;
        }
        return isExpired(e) ? ErrorCode.EXPIRED_TOKEN : ErrorCode.INVALID_TOKEN;
    }

    /**
     * 원인 예외를 따라가며 만료 검증 오류가 있는지 확인합니다.
     */
    private static boolean isExpired(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof JwtValidationException validation) {
                return validation.getErrors().stream()
                        .anyMatch(error -> error.getDescription() != null
                                && error.getDescription().startsWith(EXPIRED_DESCRIPTION_PREFIX));
            }
        }
        return false;
    }
}
