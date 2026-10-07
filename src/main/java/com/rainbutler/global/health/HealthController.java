package com.rainbutler.global.health;

import com.rainbutler.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서버 상태 확인용 컨트롤러입니다. AWS 로드밸런서 헬스 체크에 사용합니다.
 */
@Tag(name = "Health", description = "서버 상태 확인")
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    /**
     * 서버가 요청을 받을 수 있는지 확인합니다.
     *
     * @return 항상 "ok"
     */
    @Operation(summary = "헬스 체크")
    @GetMapping
    public ResponseEntity<ApiResponse<String>> health() {
        return ResponseEntity.ok(ApiResponse.ok("ok"));
    }
}
