package com.rainbutler.domain.review.dto;

import com.rainbutler.domain.review.entity.ReviewTargetType;
import java.time.LocalDateTime;

/**
 * 검토 대기 한 건입니다.
 *
 * <p>점검 · 관리 기록 · 청소를 같은 모양으로 내려주므로, 그 종류에 없는 값은 {@code null}입니다.
 *
 * @param type 검토 대상 종류
 * @param id 검토 대상 id ({@code type}과 함께 {@code PATCH /admin/reviews/{type}/{id}}에 쓴다)
 * @param createdAt 기록 생성 시각
 * @param userId 점검 · 청소한 사용자 id
 * @param nickname 사용자 닉네임
 * @param drainId 빗물받이 id
 * @param drainName 빗물받이 표시 이름 (입양 별명 우선)
 * @param beforePhotoUrl 점검 전 · 청소 전 사진 조회 URL
 * @param afterPhotoUrl 점검 후 · 청소 후 사진 조회 URL (없으면 null)
 * @param beforeGrade 점검 전 등급 (1~3, 점검 · 청소만)
 * @param afterGrade 점검 후 등급 (1~3, 점검 · 청소만)
 * @param dirtLevel 점검 전 더러움 단계 (1~3, 관리 기록만)
 * @param aiConfidence AI 확신도
 * @param distanceM 촬영 위치와 빗물받이 사이 거리 (m, 점검만)
 * @param locationMismatch 촬영 위치가 기준 거리 이상 떨어졌는지 (관리 기록 · 청소만)
 * @param reason 검토가 필요한 이유 (사람이 읽는 문구)
 */
public record ReviewItemResponse(
        ReviewTargetType type,
        Long id,
        LocalDateTime createdAt,
        Long userId,
        String nickname,
        Long drainId,
        String drainName,
        String beforePhotoUrl,
        String afterPhotoUrl,
        Integer beforeGrade,
        Integer afterGrade,
        Integer dirtLevel,
        Double aiConfidence,
        Integer distanceM,
        boolean locationMismatch,
        String reason
) {
}
