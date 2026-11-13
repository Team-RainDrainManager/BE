package com.rainbutler.domain.drain.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.rainbutler.domain.auth.client.KakaoProperties;
import com.rainbutler.global.config.AddressProperties;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 좌표의 행정동 이름(예: 역곡2동)을 찾습니다. (카카오 로컬 좌표 → 행정구역 API, 비 예보 알림 문구용)
 *
 * <p>주소 변환({@link DrainAddressResolver})과 같은 키 · 같은 설정을 쓰고, 실패해도 등록을 막지 않습니다 (null).
 * 카카오 개발자 콘솔에서 카카오맵(로컬) 사용 설정이 꺼져 있으면 403이 나므로 원인을 로그에 남깁니다.
 */
@Slf4j
@Component
public class DrainRegionResolver {

    static final String COORD_TO_REGION_URL =
            "https://dapi.kakao.com/v2/local/geo/coord2regioncode.json";
    /** 행정동 (법정동은 B) */
    private static final String ADMINISTRATIVE = "H";

    private final RestClient restClient;
    private final KakaoProperties kakaoProperties;
    private final AddressProperties addressProperties;

    public DrainRegionResolver(
            @Qualifier("kakaoRestClient") RestClient restClient,
            KakaoProperties kakaoProperties,
            AddressProperties addressProperties) {
        this.restClient = restClient;
        this.kakaoProperties = kakaoProperties;
        this.addressProperties = addressProperties;
    }

    /**
     * 조회할 수 있는 설정인지 확인합니다. (키가 없거나 주소 조회를 꺼 두면 false)
     *
     * @return 조회 가능 여부
     */
    public boolean isEnabled() {
        String clientId = kakaoProperties.clientId();
        return addressProperties.reverseGeocodingEnabled() && clientId != null
                && !clientId.isBlank() && !clientId.startsWith("${");
    }

    /**
     * 좌표의 행정동 이름을 찾습니다.
     *
     * @param latitude 위도
     * @param longitude 경도
     * @return 행정동 이름 (찾지 못하거나 실패하면 null)
     */
    public String resolve(double latitude, double longitude) {
        if (!isEnabled()) {
            return null;
        }
        String uri = UriComponentsBuilder.fromUriString(COORD_TO_REGION_URL)
                .queryParam("x", longitude)
                .queryParam("y", latitude)
                .queryParam("input_coord", "WGS84")
                .encode()
                .toUriString();
        try {
            RegionResponse response = restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + kakaoProperties.clientId())
                    .retrieve()
                    .body(RegionResponse.class);
            return administrativeDong(response);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.FORBIDDEN) {
                log.warn("행정동 조회가 거절됐습니다(403). 카카오 개발자 콘솔에서 카카오맵(로컬) 사용 설정이 켜져 있는지 "
                        + "확인하세요. 위도={}, 경도={}", latitude, longitude);
            } else {
                log.warn("행정동 조회에 실패했습니다({}). 위도={}, 경도={}", e.getStatusCode().value(),
                        latitude, longitude);
            }
            return null;
        } catch (RestClientException e) {
            log.warn("행정동 조회에 실패했습니다. 위도={}, 경도={}", latitude, longitude, e);
            return null;
        }
    }

    /**
     * 응답에서 행정동(H)의 3단계 이름을 꺼냅니다.
     */
    static String administrativeDong(RegionResponse response) {
        if (response == null || response.documents() == null) {
            return null;
        }
        return response.documents().stream()
                .filter(document -> ADMINISTRATIVE.equals(document.regionType()))
                .map(RegionDocument::region3depthName)
                .filter(name -> name != null && !name.isBlank())
                .map(String::strip)
                .findFirst()
                .orElse(null);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RegionResponse(List<RegionDocument> documents) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RegionDocument(
            @JsonProperty("region_type") String regionType,
            @JsonProperty("region_3depth_name") String region3depthName) {
    }
}
