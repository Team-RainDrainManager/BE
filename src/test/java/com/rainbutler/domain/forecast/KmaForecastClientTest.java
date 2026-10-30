package com.rainbutler.domain.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.rainbutler.domain.forecast.client.DailyRain;
import com.rainbutler.domain.forecast.client.KmaApiException;
import com.rainbutler.domain.forecast.client.KmaForecastClient;
import com.rainbutler.domain.forecast.client.KmaProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 기상청 단기예보 클라이언트의 요청 파라미터 · 응답 처리 · 오류 처리를 검증합니다.
 */
class KmaForecastClientTest {

    private static final String BASE_URL = "https://kma.test/getVilageFcst";
    private static final String KEY = "test-kma-key";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 30, 5, 15);

    @Test
    void 가장_최근_발표_예보를_요청하고_날짜별로_요약한다() throws IOException {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo(org.hamcrest.Matchers.startsWith(BASE_URL)))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("base_date", "20261030"))
                .andExpect(queryParam("base_time", "0500"))
                .andExpect(queryParam("nx", "57"))
                .andExpect(queryParam("ny", "125"))
                .andExpect(queryParam("dataType", "JSON"))
                .andExpect(queryParam("authKey", KEY))
                .andRespond(withSuccess(sample(), MediaType.APPLICATION_JSON));

        List<DailyRain> result = client(builder, KEY).fetchDailyRain(NOW, 60);

        assertThat(result).extracting(DailyRain::maxPop).containsExactly(30, 90);
        server.verify();
    }

    @Test
    void 오류_코드_응답이면_예외다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo(org.hamcrest.Matchers.startsWith(BASE_URL)))
                .andRespond(withSuccess("""
                        {"response":{"header":{"resultCode":"03","resultMsg":"NO_DATA"}}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client(builder, KEY).fetchDailyRain(NOW, 60))
                .isInstanceOf(KmaApiException.class)
                .hasMessageContaining("03")
                .hasMessageContaining("NO_DATA");
    }

    @Test
    void 활용신청_안_된_4xx는_재시도하지_않고_키를_메시지에_남기지_않는다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo(org.hamcrest.Matchers.startsWith(BASE_URL)))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"result\":{\"status\":403,\"message\":\"활용신청이 필요한 API 입니다. "
                                + KEY + "\"}}"));

        assertThatThrownBy(() -> client(builder, KEY).fetchDailyRain(NOW, 60))
                .isInstanceOf(KmaApiException.class)
                .hasMessageContaining("403")
                .hasMessageNotContaining(KEY);
        server.verify();
    }

    @Test
    void 서버_오류는_2회_재시도한_뒤_예외다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(times(3), requestTo(org.hamcrest.Matchers.startsWith(BASE_URL)))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client(builder, KEY).fetchDailyRain(NOW, 60))
                .isInstanceOf(KmaApiException.class)
                .hasMessageContaining("3회");
        server.verify();
    }

    @Test
    void 인증키가_없으면_호출하지_않고_빈_목록이다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        assertThat(client(builder, "").fetchDailyRain(NOW, 60)).isEmpty();
        assertThat(client(builder, "${KMA_AUTH_KEY}").fetchDailyRain(NOW, 60)).isEmpty();
        server.verify();
    }

    private KmaForecastClient client(RestClient.Builder builder, String key) {
        return new KmaForecastClient(builder.build(), new KmaProperties(key, BASE_URL, 57, 125));
    }

    private String sample() throws IOException {
        return new ClassPathResource("kma/vilage-fcst-sample.json")
                .getContentAsString(StandardCharsets.UTF_8);
    }
}
