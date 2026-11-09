package com.rainbutler.domain.inspection.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.TimeZone;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

/**
 * 앱이 보내는 촬영 시각을 읽는지 검증합니다. (도커 없이 돈다)
 *
 * <p>앱은 {@code Date.toISOString()}으로 UTC 시각을 보냅니다. 읽지 못하면 모든 점검이 400으로 실패합니다.
 */
class InspectionStartRequestJsonTest {

    private final JsonMapper mapper = JsonMapper.builder()
            .defaultTimeZone(TimeZone.getTimeZone("Asia/Seoul"))
            .build();

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "2026-11-09T01:00:00.000Z, 2026-11-09T01:00:00Z",
            "2026-11-09T10:00:00+09:00, 2026-11-09T01:00:00Z"
    })
    void 시간대가_있는_촬영_시각을_같은_순간으로_읽는다(String capturedAt, String expected) {
        String body = "{\"drainId\":1,\"kind\":\"ROUTINE\",\"beforeKey\":\"key\","
                + "\"latitude\":37.5,\"longitude\":127.0,\"capturedAt\":\"" + capturedAt + "\","
                + "\"safetyConfirmed\":true}";

        InspectionStartRequest request = mapper.readValue(body, InspectionStartRequest.class);

        assertThat(request.capturedAt().toInstant()).isEqualTo(Instant.parse(expected));
    }
}
