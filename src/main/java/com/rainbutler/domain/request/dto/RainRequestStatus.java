package com.rainbutler.domain.request.dto;

import com.rainbutler.domain.request.entity.RequestStatus;
import java.util.List;
import java.util.Locale;

/**
 * 지도에 보여 줄 빗물받이의 이번 비 대신 점검 상태입니다.
 */
public enum RainRequestStatus {
    /** 대신 점검이 필요함 (OPEN) */
    NEEDS_HELP(List.of(RequestStatus.OPEN)),
    /** 누군가 점검하기로 함 (SENT · ACCEPTED · CLAIMED) */
    IN_PROGRESS(List.of(RequestStatus.SENT, RequestStatus.ACCEPTED, RequestStatus.CLAIMED)),
    /** 점검을 마침 (COMPLETED) */
    DONE(List.of(RequestStatus.COMPLETED));

    private final List<RequestStatus> requestStatuses;

    RainRequestStatus(List<RequestStatus> requestStatuses) {
        this.requestStatuses = requestStatuses;
    }

    /**
     * 이 상태에 해당하는 요청 상태들입니다. (지도 필터)
     *
     * @return 요청 상태 목록
     */
    public List<RequestStatus> requestStatuses() {
        return requestStatuses;
    }

    /**
     * 요청 상태를 지도 상태로 바꿉니다. 놓침 · 종료 · 취소는 지도에 보여 주지 않습니다.
     *
     * @param status 요청 상태
     * @return 지도 상태 (보여 주지 않으면 null)
     */
    public static RainRequestStatus of(RequestStatus status) {
        for (RainRequestStatus value : values()) {
            if (value.requestStatuses.contains(status)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 요청 파라미터 문자열을 상태로 바꿉니다. 대소문자는 가리지 않습니다.
     *
     * @param value 파라미터 (null이나 빈 값이면 null)
     * @return 지도 상태
     * @throws IllegalArgumentException 모르는 값
     */
    public static RainRequestStatus from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return valueOf(value.strip().toUpperCase(Locale.ROOT));
    }
}
