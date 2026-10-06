package com.rainbutler.global.exception;

import com.rainbutler.global.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GlobalExceptionHandler 검증용 컨트롤러입니다. 테스트 소스에만 존재합니다.
 */
@RestController
@RequestMapping("/test/exceptions")
class ExceptionTestController {

    /** 비즈니스 예외를 던집니다. */
    @GetMapping("/business")
    ResponseEntity<ApiResponse<Void>> business() {
        throw new BusinessException(ErrorCode.DRAIN_NOT_FOUND);
    }

    /** 알 수 없는 예외를 던집니다. 메시지에 내부 정보가 들어 있다고 가정합니다. */
    @GetMapping("/unknown")
    ResponseEntity<ApiResponse<Void>> unknown() {
        throw new IllegalStateException("내부 비밀 정보");
    }

    /** 요청 본문 검증을 합니다. */
    @PostMapping("/valid")
    ResponseEntity<ApiResponse<Void>> valid(@Valid @RequestBody TestRequest request) {
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /**
     * 검증용 요청 본문입니다.
     *
     * @param nickname 닉네임 (공백 불가)
     */
    record TestRequest(@NotBlank String nickname) {
    }
}
