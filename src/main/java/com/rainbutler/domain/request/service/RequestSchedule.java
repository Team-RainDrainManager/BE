package com.rainbutler.domain.request.service;

import java.time.LocalDateTime;

/**
 * 비 예보 요청을 언제 보내고 마감을 언제로 할지 정한 결과입니다. {@link RequestSchedulePolicy}가 만듭니다.
 *
 * @param type 발송 방식
 * @param sentAt 발송 시각 (SEND_NOW만)
 * @param replyDeadlineAt 응답 마감 (SEND_NOW만)
 * @param completeDeadlineAt 완료 마감 (SEND_NOW만)
 * @param skipReason 보내지 않는 이유 (SKIP만)
 */
public record RequestSchedule(
        Type type,
        LocalDateTime sentAt,
        LocalDateTime replyDeadlineAt,
        LocalDateTime completeDeadlineAt,
        String skipReason) {

    /** 발송 방식입니다. */
    public enum Type {
        /** 06:00 일괄 발송 배치가 보낸다 (지금은 아무것도 하지 않는다) */
        SEND_AT_NOTICE,
        /** 지금 바로 보낸다 */
        SEND_NOW,
        /** 보내지 않는다 */
        SKIP
    }

    /**
     * 06:00 일괄 발송을 기다립니다.
     */
    public static RequestSchedule atNotice() {
        return new RequestSchedule(Type.SEND_AT_NOTICE, null, null, null, null);
    }

    /**
     * 지정한 시각 · 마감으로 바로 보냅니다.
     */
    public static RequestSchedule sendNow(
            LocalDateTime sentAt, LocalDateTime replyDeadlineAt, LocalDateTime completeDeadlineAt) {
        return new RequestSchedule(
                Type.SEND_NOW, sentAt, replyDeadlineAt, completeDeadlineAt, null);
    }

    /**
     * 보내지 않습니다.
     *
     * @param reason 이유 (로그용)
     */
    public static RequestSchedule skip(String reason) {
        return new RequestSchedule(Type.SKIP, null, null, null, reason);
    }

    /**
     * 지금 바로 보내는지 확인합니다.
     *
     * @return SEND_NOW면 true
     */
    public boolean isSendNow() {
        return type == Type.SEND_NOW;
    }
}
