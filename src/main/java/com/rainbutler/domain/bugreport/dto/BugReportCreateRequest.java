package com.rainbutler.domain.bugreport.dto;

import com.rainbutler.domain.bugreport.entity.BugReportCategory;
import com.rainbutler.domain.bugreport.entity.BugReportPlatform;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 버그 신고 요청입니다.
 *
 * @param category 분류 (ERROR · UI · FEATURE · PERFORMANCE · OTHER)
 * @param content 내용 (앞뒤 공백을 뺀 10~2000자)
 * @param screen 버그가 난 화면 · 경로 (선택, 200자 이하)
 * @param requestId 마지막으로 받은 응답 헤더 X-Request-Id (선택, 64자 이하)
 * @param appVersion 앱 버전 (선택, 50자 이하)
 * @param platform WEB · IOS · ANDROID (선택)
 * @param photoKeys 스크린샷 S3 key (선택, 최대 3개, 업로드 용도 BUG_REPORT)
 */
public record BugReportCreateRequest(
        @Schema(example = "ERROR") @NotNull BugReportCategory category,
        @Schema(example = "점검 사진을 올리면 화면이 하얗게 멈춰요.") @NotBlank @Size(max = 2000)
        String content,
        @Schema(example = "/inspections/new") @Size(max = 200) String screen,
        @Schema(example = "ab12cd34") @Size(max = 64) String requestId,
        @Schema(example = "1.0.3") @Size(max = 50) String appVersion,
        @Schema(example = "WEB") BugReportPlatform platform,
        @Size(max = 3) List<@NotBlank String> photoKeys) {
}
