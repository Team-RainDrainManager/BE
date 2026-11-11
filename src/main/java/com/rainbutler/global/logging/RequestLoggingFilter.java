package com.rainbutler.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청마다 요청 ID를 붙이고, 요청이 끝나면 접근 로그를 한 줄 남깁니다. (보안 필터보다 앞에서 실행)
 *
 * <ul>
 *   <li>요청 ID: 요청 헤더 {@code X-Request-Id}가 있으면 그대로, 없으면 UUID 앞 8자리. MDC {@code requestId}에
 *       넣고 응답 헤더 {@code X-Request-Id}로도 돌려준다 (프론트가 버그 제보 때 알려주면 로그를 바로 찾을 수 있다)</li>
 *   <li>접근 로그(logger {@code access}): method · uri(쿼리스트링 제외) · status · durationMs · userId(인증된
 *       경우) · clientIp(X-Forwarded-For 첫 값)</li>
 *   <li>{@code /actuator/**}는 남기지 않는다 (수집기 요청이 로그를 덮지 않게)</li>
 *   <li>Authorization 헤더 · 토큰 · 비밀번호 · 요청 · 응답 바디 · 쿼리스트링(presigned URL 서명 등)은 남기지 않는다</li>
 *   <li>요청이 끝나면 MDC를 비운다</li>
 * </ul>
 */
public class RequestLoggingFilter extends OncePerRequestFilter {

    /** 요청 · 응답 헤더 이름 */
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    /** MDC 키 */
    public static final String REQUEST_ID = "requestId";
    /** MDC 키 (인증된 요청만) */
    public static final String USER_ID = "userId";
    /** 인증 필터가 사용자 id를 남겨 두는 요청 속성 (보안 컨텍스트는 요청 끝에 비워지므로) */
    public static final String USER_ID_ATTRIBUTE = RequestLoggingFilter.class.getName() + ".userId";

    private static final Logger ACCESS_LOG = LoggerFactory.getLogger("access");
    /** 들어온 요청 ID는 이 형식만 받는다 (로그 위조 · 과도한 길이 방지) */
    private static final Pattern VALID_REQUEST_ID = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
    private static final String ACTUATOR_PATH = "/actuator";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.equals(ACTUATOR_PATH) || uri.startsWith(ACTUATOR_PATH + "/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String requestId = resolveRequestId(request.getHeader(REQUEST_ID_HEADER));
        MDC.put(REQUEST_ID, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        long startedAt = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            Object userId = request.getAttribute(USER_ID_ATTRIBUTE);
            String clientIp = clientIp(request);
            ACCESS_LOG.atInfo()
                    .addKeyValue("method", request.getMethod())
                    .addKeyValue("uri", request.getRequestURI())
                    .addKeyValue("status", response.getStatus())
                    .addKeyValue("durationMs", durationMs)
                    .addKeyValue("userId", userId)
                    .addKeyValue("clientIp", clientIp)
                    .log("{} {} {} {}ms user={} ip={}", request.getMethod(),
                            request.getRequestURI(), response.getStatus(), durationMs,
                            userId == null ? "-" : userId, clientIp);
            MDC.remove(REQUEST_ID);
            MDC.remove(USER_ID);
        }
    }

    private static String resolveRequestId(String incoming) {
        if (incoming != null && VALID_REQUEST_ID.matcher(incoming).matches()) {
            return incoming;
        }
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].strip();
        }
        return request.getRemoteAddr();
    }
}
