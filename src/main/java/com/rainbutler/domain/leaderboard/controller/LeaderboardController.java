package com.rainbutler.domain.leaderboard.controller;

import com.rainbutler.domain.leaderboard.dto.LeaderboardResponse;
import com.rainbutler.domain.leaderboard.dto.MyRankResponse;
import com.rainbutler.domain.leaderboard.service.LeaderboardQueryService;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.response.ApiResponse;
import com.rainbutler.global.security.LoginUserId;
import com.rainbutler.global.swagger.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 리더보드(주간 · 누적 참여 점수 순위) API입니다.
 */
@Tag(name = "Leaderboard", description = "리더보드")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/leaderboard")
public class LeaderboardController {

    private static final String LIST = """
            주간 · 누적 참여 점수 순위를 조회합니다.

            - `type`: `WEEKLY`(기본, 이번 주 월요일 00:00 ~ 현재) · `TOTAL`(전체 누적)
            - `limit`: 상위 순위 개수. 기본 20, 최대 100입니다.
            - 점수는 점검 · 제보로 적립된 점수에서 회수된 점수를 뺀 값입니다. 포인트 이력이 없는
              사용자도 0점으로 순위에 나오고, 음수 점수와 관리자 · 시청 관계자는 빠집니다.
            - 동점이면 같은 순위를 주고 다음 순위를 건너뜁니다. (예: 1, 1, 3)
            - `rankings`는 상위 순위이고, `me`는 내 순위입니다. 내가 상위 순위 밖이어도 `me`에 내려갑니다.
            - `me.rank`는 점수가 음수이면 `null`이고, `me.score`는 실제 값(음수 포함)입니다.
            - 구역(`ZONE`) · 단체(`ORG`) 순위는 구역 · 단체 테이블이 정해지기 전까지 지원하지 않습니다.
            - 로그인이 필요합니다.
            """;

    private static final String MY_RANK = """
            내 리더보드 순위만 조회합니다.

            - `type`: `WEEKLY`(기본) · `TOTAL`
            - 점수가 음수이면 `rank`는 `null`이고, `score`는 실제 값(음수 포함)입니다.
            - 상위 순위 목록은 `GET /api/v1/leaderboard`로 조회합니다.
            - 로그인이 필요합니다.
            """;

    private final LeaderboardQueryService leaderboardQueryService;

    /**
     * 리더보드 상위 순위와 내 순위를 조회합니다.
     */
    @Operation(summary = "리더보드 조회", description = LIST)
    @ApiErrorCodes({ErrorCode.INVALID_INPUT, ErrorCode.USER_NOT_FOUND, ErrorCode.UNAUTHORIZED,
            ErrorCode.EXPIRED_TOKEN, ErrorCode.INVALID_TOKEN})
    @GetMapping
    public ApiResponse<LeaderboardResponse> findRankings(
            @LoginUserId Long userId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer limit
    ) {
        return ApiResponse.ok(leaderboardQueryService.findRankings(userId, type, limit));
    }

    /**
     * 내 리더보드 순위를 조회합니다.
     */
    @Operation(summary = "내 리더보드 순위", description = MY_RANK)
    @ApiErrorCodes({ErrorCode.INVALID_INPUT, ErrorCode.USER_NOT_FOUND, ErrorCode.UNAUTHORIZED,
            ErrorCode.EXPIRED_TOKEN, ErrorCode.INVALID_TOKEN})
    @GetMapping("/me")
    public ApiResponse<MyRankResponse> findMyRank(
            @LoginUserId Long userId,
            @RequestParam(required = false) String type
    ) {
        return ApiResponse.ok(leaderboardQueryService.findMyRank(userId, type));
    }
}
