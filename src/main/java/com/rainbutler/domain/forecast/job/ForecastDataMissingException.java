package com.rainbutler.domain.forecast.job;

/**
 * 기상청 응답에 오늘 · 내일 중 한 날짜의 예보가 빠져 있습니다.
 *
 * <p>던지지 않고 ERROR 로그에 담아 남깁니다. 그 날짜의 예보 상태는 바꾸지 않고(데이터가 없다고 취소하지 않는다)
 * 다음 수집 회차를 기다립니다. 개발자가 로그에서 이 예외 이름이나 {@code [비 예보 수집 누락]}으로 찾을 수 있습니다.
 */
public class ForecastDataMissingException extends RuntimeException {

    /**
     * 누락 내용으로 예외를 만듭니다.
     *
     * @param message 누락된 날짜와 받은 날짜
     */
    public ForecastDataMissingException(String message) {
        super(message);
    }
}
