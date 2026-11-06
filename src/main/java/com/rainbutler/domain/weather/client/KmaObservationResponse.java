package com.rainbutler.domain.weather.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.rainbutler.domain.forecast.client.KmaForecastResponse;
import java.util.List;

/**
 * 기상청 초단기실황 조회 응답입니다. 필요한 필드만 받습니다. (헤더 · API허브 오류는 단기예보와 같은 형식)
 *
 * @param response 정상 응답 (헤더 + 본문)
 * @param result API허브 오류
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KmaObservationResponse(Response response, KmaForecastResponse.HubResult result) {

    /** 응답 헤더 · 본문입니다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(KmaForecastResponse.Header header, Body body) {
    }

    /** 실황 항목 목록입니다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(Items items) {
    }

    /** 실황 항목 목록입니다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Items(List<Item> item) {
    }

    /**
     * 실황 항목 하나입니다.
     *
     * @param category 항목 (PTY 강수형태, RN1 1시간 강수량 등)
     * @param obsrValue 관측 값
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String category, String obsrValue) {
    }
}
