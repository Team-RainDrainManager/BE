package com.rainbutler.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT 인증이 끝난 뒤 사용자 id를 MDC {@code userId}와 요청 속성에 남깁니다.
 *
 * <p>보안 컨텍스트는 요청이 끝날 때 비워지므로, 접근 로그({@link RequestLoggingFilter})는 이 요청 속성으로
 * 사용자 id를 읽습니다. 스프링 빈으로 등록하지 않고 보안 필터 체인에만 넣습니다 (서블릿 필터로 한 번 더 등록되지 않게).
 */
public class AuthenticatedUserMdcFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            String userId = token.getToken().getSubject();
            MDC.put(RequestLoggingFilter.USER_ID, userId);
            request.setAttribute(RequestLoggingFilter.USER_ID_ATTRIBUTE, userId);
        }
        chain.doFilter(request, response);
    }
}
