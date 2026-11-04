package com.rainbutler.domain.drain.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 지도 · 목록에서 보여주는 빗물받이 표시 상태입니다.
 *
 * <p>DB에 저장하지 않고 조회할 때마다 계산합니다. 우선순위는 막힘 제보 → 미입양 → 점검 지연 → 정상입니다.
 *
 * <p>{@code status} 값은 프론트 지도 화면이 쓰는 키(unadopted · normal · overdue · blocked)와 맞춥니다.
 */
@Getter
@RequiredArgsConstructor
public enum DrainDisplayStatus {
    /** 입양되지 않음 */
    AVAILABLE("미입양", "unadopted"),
    /** 입양되어 있고 다음 점검일이 지나지 않음 */
    NORMAL("정상", "normal"),
    /** 입양되어 있지만 다음 점검일이 지남 */
    MANAGEMENT_DELAYED("점검 지연", "overdue"),
    /** 처리 대기 중인 막힘 제보가 있음 */
    BLOCKAGE_REPORTED("막힘 제보", "blocked");

    private final String label;
    /** 지도 · 목록 API의 {@code status} 값 */
    private final String key;

    /**
     * API 파라미터를 표시 상태로 바꿉니다. 영문 이름 · 지도 키 · 한국어 라벨을 모두 받습니다.
     *
     * @param value 요청 값 (비어 있거나 {@code all}이면 전체)
     * @return 표시 상태 (전체면 null)
     * @throws IllegalArgumentException 알 수 없는 값
     */
    public static DrainDisplayStatus from(String value) {
        if (value == null || value.isBlank() || "all".equalsIgnoreCase(value.trim())) {
            return null;
        }
        String trimmed = value.trim();
        for (DrainDisplayStatus candidate : values()) {
            if (candidate.name().equalsIgnoreCase(trimmed)
                    || candidate.key.equalsIgnoreCase(trimmed)
                    || candidate.label.equals(trimmed)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException(value);
    }
}
