package com.rainbutler.global.security;

import com.rainbutler.global.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 경로 인가 검증용 컨트롤러입니다. 테스트 소스에만 존재합니다.
 */
@RestController
class AdminTestController {

    /** ADMIN · CITY_OFFICIAL만 접근할 수 있는 경로 */
    @GetMapping("/api/v1/admin/test-only")
    ResponseEntity<ApiResponse<String>> adminOnly() {
        return ResponseEntity.ok(ApiResponse.ok("admin"));
    }
}
