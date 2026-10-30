package com.rainbutler.domain.forecast.client;

/**
 * 기상청 API 호출 실패입니다. (네트워크 오류, 오류 응답 코드, 형식이 다른 응답)
 *
 * <p>수집 배치가 잡아서 로그만 남기고, 이번 회차는 예보를 바꾸지 않습니다.
 */
public class KmaApiException extends RuntimeException {

    /**
     * 실패 이유로 예외를 만듭니다. 메시지에 인증키를 넣지 않습니다.
     *
     * @param message 실패 이유
     */
    public KmaApiException(String message) {
        super(message);
    }
}
