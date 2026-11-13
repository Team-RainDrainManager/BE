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
 * 좌표를 주소로 바꿉니다. (카카오 로컬 API 역지오코딩)
 *
 * <p>주소는 화면 표시용이라 조회에 실패해도 빗물받이 등록을 막지 않습니다. 설정이 없거나 카카오가 응답하지
 * 않으면 {@code null}을 돌려주고, 등록은 그대로 진행합니다.
 */
@Slf4j
@Component
public class DrainAddressResolver {

    static final String COORD_TO_ADDRESS_URL =
            "https://dapi.kakao.com/v2/local/geo/coord2address.json";

    private final RestClient restClient;
    private final KakaoProperties kakaoProperties;
    private final AddressProperties addressProperties;

    public DrainAddressResolver(
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
        return addressProperties.reverseGeocodingEnabled() && hasClientId();
    }

    /**
     * 좌표에 해당하는 도로명 주소를 찾습니다. 없으면 지번 주소를 씁니다.
     *
     * @param latitude 위도
     * @param longitude 경도
     * @return 주소 (찾지 못하면 null)
     */
    public String resolve(double latitude, double longitude) {
        if (!isEnabled()) {
            return null;
        }
        try {
            return lookup(latitude, longitude);
        } catch (AddressLookupForbiddenException e) {
            return null;
        }
    }

    /**
     * 백필용 조회입니다. {@link #resolve}와 같지만 403(카카오맵 사용 설정 꺼짐)이면 예외를 던져 백필을 멈추게 합니다.
     * (빗물받이마다 같은 거절을 반복 호출 · 로그하지 않게)
     *
     * @param latitude 위도
     * @param longitude 경도
     * @return 주소 (찾지 못하면 null)
     * @throws AddressLookupForbiddenException 카카오가 403으로 거절
     */
    public String resolveForBackfill(double latitude, double longitude) {
        return isEnabled() ? lookup(latitude, longitude) : null;
    }

    private String lookup(double latitude, double longitude) {
        String uri = UriComponentsBuilder.fromUriString(COORD_TO_ADDRESS_URL)
                .queryParam("x", longitude)
                .queryParam("y", latitude)
                .queryParam("input_coord", "WGS84")
                .encode()
                .toUriString();

        try {
            CoordToAddressResponse response = restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + kakaoProperties.clientId())
                    .retrieve()
                    .body(CoordToAddressResponse.class);
            return firstAddress(response);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.FORBIDDEN) {
                log.warn("주소 역지오코딩이 거절됐습니다(403). 카카오 개발자 콘솔에서 카카오맵(로컬) 사용 설정이 "
                        + "켜져 있는지 확인하세요. 위도={}, 경도={}", latitude, longitude);
                throw new AddressLookupForbiddenException();
            }
            log.warn("빗물받이 주소 역지오코딩에 실패했습니다({}). 위도={}, 경도={}",
                    e.getStatusCode().value(), latitude, longitude);
            return null;
        } catch (RestClientException e) {
            log.warn("빗물받이 주소 역지오코딩에 실패했습니다. 위도={}, 경도={}", latitude, longitude, e);
            return null;
        }
    }

    private boolean hasClientId() {
        String clientId = kakaoProperties.clientId();
        return clientId != null && !clientId.isBlank() && !clientId.startsWith("${");
    }

    private String firstAddress(CoordToAddressResponse response) {
        if (response == null || response.documents() == null || response.documents().isEmpty()) {
            return null;
        }
        CoordToAddressDocument document = response.documents().get(0);
        if (document.roadAddress() != null && hasText(document.roadAddress().addressName())) {
            return document.roadAddress().addressName().trim();
        }
        if (document.address() != null && hasText(document.address().addressName())) {
            return document.address().addressName().trim();
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 카카오가 주소 조회를 403으로 거절했습니다. (카카오맵 사용 설정 꺼짐)
     */
    public static class AddressLookupForbiddenException extends RuntimeException {

        AddressLookupForbiddenException() {
            super("카카오 주소 역지오코딩 거절(403)");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CoordToAddressResponse(List<CoordToAddressDocument> documents) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CoordToAddressDocument(
            @JsonProperty("road_address") CoordToAddressRoadAddress roadAddress,
            @JsonProperty("address") CoordToAddressJibunAddress address) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CoordToAddressRoadAddress(@JsonProperty("address_name") String addressName) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CoordToAddressJibunAddress(@JsonProperty("address_name") String addressName) {
    }
}
