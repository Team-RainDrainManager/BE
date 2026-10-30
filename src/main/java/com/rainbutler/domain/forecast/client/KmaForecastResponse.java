package com.rainbutler.domain.forecast.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * 기상청 단기예보 조회 응답입니다. 필요한 필드만 받습니다.
 *
 * <p>정상 응답은 {@code response}에 담기고, API허브 자체 오류(인증 · 활용신청 등)는 {@code result}에 담깁니다.
 *
 * @param response 정상 응답 (헤더 + 본문)
 * @param result API허브 오류
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KmaForecastResponse(Response response, HubResult result) {

    /** 응답 헤더 · 본문입니다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(Header header, Body body) {
    }

    /** 결과 코드 ({@code 00}이면 정상) · 메시지입니다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Header(String resultCode, String resultMsg) {
    }

    /** 예보 항목 목록 · 전체 개수입니다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(Items items, Integer totalCount) {
    }

    /** 예보 항목 목록입니다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Items(List<Item> item) {
    }

    /**
     * 예보 항목 하나입니다.
     *
     * @param category 항목 (POP 강수확률, PCP 1시간 강수량 등)
     * @param fcstDate 예보 날짜 (yyyyMMdd)
     * @param fcstTime 예보 시각 (HHmm)
     * @param fcstValue 예보 값
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String category, String fcstDate, String fcstTime, String fcstValue) {
    }

    /** API허브 오류 상태 · 메시지입니다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HubResult(Integer status, String message) {
    }
}
