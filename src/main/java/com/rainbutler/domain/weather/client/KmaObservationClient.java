package com.rainbutler.domain.weather.client;

import com.rainbutler.domain.forecast.client.KmaApiException;
import com.rainbutler.domain.forecast.client.KmaBaseTime;
import com.rainbutler.domain.forecast.client.KmaProperties;
import com.rainbutler.domain.forecast.client.KmaRequestExecutor;
import com.rainbutler.domain.forecast.client.PcpParser;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 기상청 초단기실황(getUltraSrtNcst) 클라이언트입니다. 파일럿 격자의 지금 강수형태 · 1시간 강수량을 가져옵니다.
 *
 * <p>호출 · 재시도 · 인증키 가리기는 단기예보와 같이 {@link KmaRequestExecutor}가 합니다. 인증키가 없으면 호출하지
 * 않고 빈 값을 돌려줍니다 (CI · 로컬 키 없는 환경).
 */
@Slf4j
@Component
public class KmaObservationClient {

    private static final int NUM_OF_ROWS = 20;
    private static final String PTY = "PTY";
    private static final String RN1 = "RN1";

    private final KmaRequestExecutor executor;
    private final KmaProperties properties;
    private final AtomicBoolean missingKeyWarned = new AtomicBoolean(false);

    public KmaObservationClient(KmaRequestExecutor executor, KmaProperties properties) {
        this.executor = executor;
        this.properties = properties;
    }

    /**
     * 가장 최근 발표된 초단기실황을 가져옵니다.
     *
     * @param now 현재 시각 (발표 시각 계산용)
     * @return 관측 결과 (인증키가 없으면 비어 있음)
     * @throws KmaApiException 호출 실패 · 오류 응답
     */
    public Optional<Observation> fetch(LocalDateTime now) {
        if (!executor.isConfigured()) {
            if (missingKeyWarned.compareAndSet(false, true)) {
                log.warn("기상청 인증키(KMA_AUTH_KEY)가 설정되지 않아 현재 날씨 수집을 건너뜁니다.");
            }
            return Optional.empty();
        }
        LocalDateTime base = KmaBaseTime.latestObservation(now);
        KmaObservationResponse response = executor.get(
                properties.ultraSrtNcstUrl(), base, NUM_OF_ROWS, KmaObservationResponse.class);
        return Optional.of(toObservation(response, base));
    }

    /**
     * 응답을 관측 결과로 바꿉니다.
     *
     * @param response 기상청 응답
     * @param observedAt 발표 시각
     * @return 관측 결과
     */
    static Observation toObservation(KmaObservationResponse response, LocalDateTime observedAt) {
        if (response == null) {
            throw new KmaApiException("기상청 응답이 비어 있습니다.");
        }
        KmaRequestExecutor.requireSuccess(response.result(),
                response.response() == null ? null : response.response().header());
        List<KmaObservationResponse.Item> items = Optional.ofNullable(response.response().body())
                .map(KmaObservationResponse.Body::items)
                .map(KmaObservationResponse.Items::item)
                .orElse(List.of());
        Integer pty = null;
        BigDecimal rn1 = null;
        for (KmaObservationResponse.Item item : items) {
            if (PTY.equals(item.category())) {
                pty = parsePty(item.obsrValue());
            } else if (RN1.equals(item.category())) {
                rn1 = PcpParser.parse(item.obsrValue());
            }
        }
        return new Observation(pty, rn1, observedAt);
    }

    private static Integer parsePty(String value) {
        try {
            return value == null ? null : Integer.valueOf(value.strip());
        } catch (NumberFormatException e) {
            log.warn("[기상청 실황] 강수형태가 숫자가 아닙니다: {}", value);
            return null;
        }
    }
}
