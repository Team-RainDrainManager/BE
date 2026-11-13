package com.rainbutler.domain.bugreport.dto;

import com.rainbutler.domain.bugreport.entity.BugReportStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 관리자 버그 신고 처리 요청입니다. 둘 다 선택이고, 보낸 값만 바꿉니다.
 *
 * @param status 처리 상태 (RESOLVED · WONT_FIX면 처리자 · 처리 시각 기록, 다시 열면 지움)
 * @param adminNote 관리자 메모 (1000자 이하)
 */
public record BugReportUpdateRequest(
        @Schema(example = "RESOLVED") BugReportStatus status,
        @Schema(example = "1.0.4에서 수정") @Size(max = 1000) String adminNote) {
}
