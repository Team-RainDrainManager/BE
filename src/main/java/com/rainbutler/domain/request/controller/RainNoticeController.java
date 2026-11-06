package com.rainbutler.domain.request.controller;

import com.rainbutler.domain.request.dto.RainNoticeResponse;
import com.rainbutler.domain.request.service.RainNoticeService;
import com.rainbutler.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 지도 비 예보 배너 API입니다. 로그인 없이 볼 수 있습니다.
 */
@Tag(name = "Request", description = "비 예보 점검 요청")
@RestController
@RequestMapping("/api/v1/rain-notice")
@RequiredArgsConstructor
public class RainNoticeController {

    private static final String NOTICE = """
            지도 화면 위에 띄울 비 예보 배너입니다. **로그인 없이** 볼 수 있고 개인 정보는 없습니다.
            모양 · 문구 위치는 프론트가 정하고, 백엔드는 상태 · 값 · 기본 문구(`message`)만 줍니다.

            | state | 언제 | 내용 |
            | --- | --- | --- |
            | `RAINING` | 지금 비가 오는 중 (기상청 초단기실황, 매시 45분 갱신) | `message`는 "비가 오고 있어요."만 |
            | `FORECAST` | 오늘 · 내일 비 예보가 있음 | 예보 · 가장 이른 완료 마감 · 대신 점검 진행 상황 |
            | `NONE` | 둘 다 아님 | 배너 없음 |

            - 우선순위: `RAINING` > `FORECAST` > `NONE`. `RAINING`이어도 예보가 있으면 `forecast` · `help`를 같이 준다
            - 이번 비 예보: 오늘 · 내일 진행 중 예보 중 가까운 날짜, 같은 날짜면 실제 예보 우선
              (가상 예보는 실제 예보가 없을 때만)
            - `help`: `needsHelpCount` 대신 점검 필요(OPEN) · `inProgressCount` 진행 중(SENT · ACCEPTED · CLAIMED) ·
              `doneCount` 완료(COMPLETED). 놓침 · 종료 · 취소는 빠진다
            - 실황이 3시간보다 오래되면 비 아님으로 본다
            """;

    private final RainNoticeService rainNoticeService;

    /**
     * 지금 비 예보 배너를 조회합니다.
     */
    @Operation(summary = "지도 비 예보 배너", description = NOTICE)
    @SecurityRequirements
    @GetMapping
    public ResponseEntity<ApiResponse<RainNoticeResponse>> current() {
        return ResponseEntity.ok(ApiResponse.ok(rainNoticeService.current()));
    }
}
