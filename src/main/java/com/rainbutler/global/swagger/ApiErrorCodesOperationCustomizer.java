package com.rainbutler.global.swagger;

import com.rainbutler.global.exception.ErrorCode;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

/**
 * {@link ApiErrorCodes}를 읽어 Swagger 응답에 HTTP 상태별 실패 JSON 예시를 추가합니다.
 */
@Component
public class ApiErrorCodesOperationCustomizer implements OperationCustomizer {

    private static final String JSON = org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        ApiErrorCodes annotation = handlerMethod.getMethodAnnotation(ApiErrorCodes.class);
        if (annotation == null) {
            return operation;
        }

        // 같은 HTTP 상태의 에러 코드는 한 응답 안에 예시 여러 개로 묶는다
        Map<Integer, List<ErrorCode>> byStatus = new TreeMap<>();
        for (ErrorCode errorCode : annotation.value()) {
            byStatus.computeIfAbsent(errorCode.getStatus().value(), k -> new ArrayList<>())
                    .add(errorCode);
        }

        ApiResponses responses = operation.getResponses() == null
                ? new ApiResponses() : operation.getResponses();
        byStatus.forEach((status, errorCodes) -> {
            MediaType mediaType = new MediaType();
            errorCodes.forEach(errorCode ->
                    mediaType.addExamples(errorCode.name(), toExample(errorCode)));
            responses.addApiResponse(String.valueOf(status), new ApiResponse()
                    .description(describe(errorCodes))
                    .content(new Content().addMediaType(JSON, mediaType)));
        });
        operation.setResponses(responses);
        return operation;
    }

    /**
     * 실패 응답 예시를 만듭니다. 입력값 오류에는 fieldErrors 예시를 넣습니다.
     */
    private static Example toExample(ErrorCode errorCode) {
        List<Map<String, String>> fieldErrors = errorCode == ErrorCode.INVALID_INPUT
                ? List.of(Map.of("field", "nickname", "reason", "공백일 수 없습니다"))
                : List.of();

        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", errorCode.name());
        error.put("message", errorCode.getMessage());
        error.put("fieldErrors", fieldErrors);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("data", null);
        body.put("error", error);

        return new Example().summary(errorCode.name()).value(body);
    }

    private static String describe(List<ErrorCode> errorCodes) {
        return String.join(" / ", errorCodes.stream().map(ErrorCode::name).toList());
    }
}
