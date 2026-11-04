package com.rainbutler.domain.activity.dto;

import com.rainbutler.domain.activity.entity.ActivityType;
import java.time.LocalDateTime;

/**
 * 활동 내역 한 건입니다.
 *
 * @param type 활동 종류 (INSPECTION · REPORT · POINT)
 * @param id 원본 행 id (type과 함께 한 건을 식별)
 * @param occurredAt 활동 시각
 * @param title 활동 제목 (점검 종류 · 제보 종류 · 경험치 사유)
 * @param description 활동 설명 (점검 · 제보 상태, 경험치 출처)
 * @param amount 획득 포인트 (점검 경험치 · 경험치 내역. 적립은 양수, 회수는 음수. 없으면 null)
 * @param drainId 관련 빗물받이 id (없으면 null)
 * @param drainName 빗물받이 표시 이름 (입양 별명 우선, 없으면 null)
 * @param status 점검 · 제보 상태 이름 (POINT면 null)
 */
public record ActivityResponse(
        ActivityType type,
        Long id,
        LocalDateTime occurredAt,
        String title,
        String description,
        Integer amount,
        Long drainId,
        String drainName,
        String status
) {
}
