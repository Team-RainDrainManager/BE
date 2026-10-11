package com.rainbutler.global.security;

import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@link LoginUserId} 파라미터에 access 토큰의 {@code sub}(userId)를 넣습니다.
 */
public class LoginUserIdArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginUserId.class)
                && Long.class.equals(parameter.getParameterType());
    }

    @Override
    public Long resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // 인증이 필요 없는 경로에 붙였거나 토큰이 없으면 로그인이 필요하다고 응답한다
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        try {
            return Long.valueOf(token.getToken().getSubject());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
    }
}
