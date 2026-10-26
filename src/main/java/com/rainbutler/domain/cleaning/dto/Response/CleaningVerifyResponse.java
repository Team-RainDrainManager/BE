package com.rainbutler.domain.cleaning.dto.Response;

/**
 * 청소 완료 인증 응답입니다.
 *
 * @param cleaningId 청소 id
 * @param beforeGrade 청소 전 AI 판정 등급 (0~3)
 * @param afterGrade 청소 후 AI 판정 등급 (0~3)
 * @param passed 인증 통과 여부 (afterGrade < beforeGrade)
 * @param attemptNo 인증 시도 횟수
 */
public record CleaningVerifyResponse(
        Long cleaningId,
        int beforeGrade,
        int afterGrade,
        boolean passed,
        int attemptNo
) {
}
