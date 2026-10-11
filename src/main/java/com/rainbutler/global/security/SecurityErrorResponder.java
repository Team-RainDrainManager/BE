package com.rainbutler.global.security;

import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.response.ApiResponse;
import com.rainbutler.global.response.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 보안 필터 단계(컨트롤러 밖)의 인증 · 인가 실패를 {@link ApiResponse} JSON으로 씁니다.
 */
@Component
@RequiredArgsConstructor
public class SecurityErrorResponder {

    private final JsonMapper jsonMapper;

    /**
     * 실패 응답을 씁니다.
     *
     * @param response HTTP 응답
     * @param errorCode 에러 코드
     * @throws IOException 응답 쓰기 실패
     */
    public void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // 한국어 메시지가 깨지지 않게 명시한다
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getWriter(), ApiResponse.fail(ErrorResponse.of(errorCode)));
    }
}
