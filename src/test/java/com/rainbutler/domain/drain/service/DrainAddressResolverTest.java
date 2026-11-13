package com.rainbutler.domain.drain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.rainbutler.domain.auth.client.KakaoProperties;
import com.rainbutler.global.config.AddressProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 좌표 → 주소 역지오코딩의 응답 파싱과 실패 시 null 처리를 검증합니다.
 */
class DrainAddressResolverTest {

    private static final String ROAD_ADDRESS_RESPONSE = """
            {
              "meta": { "total_count": 1 },
              "documents": [
                {
                  "road_address": { "address_name": "경기도 부천시 원미구 지봉로 43" },
                  "address": { "address_name": "경기도 부천시 원미구 역곡동 1" }
                }
              ]
            }
            """;

    private static final String JIBUN_ADDRESS_RESPONSE = """
            {
              "meta": { "total_count": 1 },
              "documents": [
                {
                  "road_address": null,
                  "address": { "address_name": "경기도 부천시 원미구 역곡동 1" }
                }
              ]
            }
            """;

    @Test
    void 도로명_주소를_먼저_돌려준다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DrainAddressResolver resolver = resolver(builder.build(), true);

        server.expect(requestTo(containsString("coord2address.json")))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "KakaoAK test-key"))
                .andRespond(withSuccess(ROAD_ADDRESS_RESPONSE, MediaType.APPLICATION_JSON));

        assertThat(resolver.resolve(37.5, 126.8))
                .isEqualTo("경기도 부천시 원미구 지봉로 43");
        server.verify();
    }

    @Test
    void 도로명_주소가_없으면_지번_주소를_쓴다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DrainAddressResolver resolver = resolver(builder.build(), true);

        server.expect(requestTo(containsString("coord2address.json")))
                .andRespond(withSuccess(JIBUN_ADDRESS_RESPONSE, MediaType.APPLICATION_JSON));

        assertThat(resolver.resolve(37.5, 126.8))
                .isEqualTo("경기도 부천시 원미구 역곡동 1");
    }

    @Test
    void 카카오가_실패하면_예외_없이_null을_돌려준다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DrainAddressResolver resolver = resolver(builder.build(), true);

        server.expect(requestTo(containsString("coord2address.json")))
                .andRespond(withServerError());

        assertThat(resolver.resolve(37.5, 126.8)).isNull();
    }

    @Test
    void 설정이_꺼져_있으면_외부_호출_없이_null을_돌려준다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DrainAddressResolver resolver = resolver(builder.build(), false);

        assertThat(resolver.resolve(37.5, 126.8)).isNull();
        server.verify();
    }

    @Test
    void 카카오가_403으로_거절하면_일반_조회는_null_백필은_예외를_던진다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DrainAddressResolver resolver = resolver(builder.build(), true);

        server.expect(requestTo(containsString("coord2address.json")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));
        server.expect(requestTo(containsString("coord2address.json")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThat(resolver.resolve(37.5, 126.8)).isNull();
        assertThatThrownBy(() -> resolver.resolveForBackfill(37.5, 126.8))
                .isInstanceOf(DrainAddressResolver.AddressLookupForbiddenException.class);
        server.verify();
    }

    private DrainAddressResolver resolver(RestClient restClient, boolean enabled) {
        return new DrainAddressResolver(
                restClient,
                new KakaoProperties("test-key", "test-secret", List.of()),
                new AddressProperties(enabled));
    }
}
