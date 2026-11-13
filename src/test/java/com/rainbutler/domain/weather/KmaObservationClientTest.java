package com.rainbutler.domain.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.rainbutler.domain.forecast.client.KmaProperties;
import com.rainbutler.domain.forecast.client.KmaRequestExecutor;
import com.rainbutler.domain.weather.client.KmaObservationClient;
import com.rainbutler.domain.weather.client.Observation;
import com.rainbutler.global.geo.KmaGridConverter.Grid;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 기상청 초단기실황 클라이언트의 요청 · 파싱과 비 판단을 검증합니다.
 */
class KmaObservationClientTest {

    private static final Grid GRID = new Grid(57, 125);
    private static final String NCST_URL = "https://kma.test/getUltraSrtNcst";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 6, 15, 45);

    @Test
    void 실제_응답에서_강수형태와_강수량을_읽는다() throws IOException {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo(Matchers.startsWith(NCST_URL)))
                .andExpect(queryParam("base_date", "20261106"))
                .andExpect(queryParam("base_time", "1500"))
                .andExpect(queryParam("nx", "57"))
                .andExpect(queryParam("ny", "125"))
                .andRespond(withSuccess(new ClassPathResource(
                        "kma/ultra-srt-ncst-real-20261106-1500.json")
                        .getContentAsString(StandardCharsets.UTF_8), MediaType.APPLICATION_JSON));

        Observation observation = client(builder, "test-key").fetch(GRID, NOW).orElseThrow();

        assertThat(observation.pty()).isZero();
        assertThat(observation.rn1Mm()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(observation.observedAt()).isEqualTo(LocalDateTime.of(2026, 11, 6, 15, 0));
        assertThat(observation.raining()).isFalse();
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({"0, false", "1, true", "2, true", "3, false", "5, true", "6, true", "7, false"})
    void 비_비눈_빗방울_빗방울눈날림만_비로_본다(int pty, boolean raining) {
        assertThat(new Observation(pty, BigDecimal.ONE, NOW).raining()).isEqualTo(raining);
        assertThat(new Observation(null, null, NOW).raining()).isFalse();
    }

    @Test
    void 인증키가_없으면_호출하지_않는다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        Optional<Observation> result = client(builder, "").fetch(GRID, NOW);

        assertThat(result).isEmpty();
        server.verify();
    }

    private KmaObservationClient client(RestClient.Builder builder, String key) {
        KmaProperties properties = new KmaProperties(key, "https://kma.test/getVilageFcst",
                NCST_URL, 57, 125, 200);
        return new KmaObservationClient(
                new KmaRequestExecutor(builder.build(), properties), properties);
    }
}
