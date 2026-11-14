package com.rainbutler.domain.request.service;

import com.rainbutler.domain.forecast.entity.RainForecast;
import com.rainbutler.domain.forecast.service.CurrentRainForecastFinder;
import com.rainbutler.domain.request.dto.RainNoticeResponse;
import com.rainbutler.domain.request.dto.RainNoticeResponse.ForecastSummary;
import com.rainbutler.domain.request.dto.RainNoticeResponse.HelpSummary;
import com.rainbutler.domain.request.dto.RainNoticeResponse.State;
import com.rainbutler.domain.request.entity.RequestStatus;
import com.rainbutler.domain.request.repository.InspectionRequestRepository;
import com.rainbutler.domain.weather.service.CurrentWeatherService;
import com.rainbutler.domain.weather.service.CurrentWeatherService.WeatherStatus;
import com.rainbutler.global.geo.KmaGridConverter.Grid;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지도 화면 위에 띄울 비 예보 배너를 만듭니다. 지도 중심 좌표의 기상청 격자 기준입니다.
 *
 * <ul>
 *   <li>지금 비가 오면 {@code RAINING} — 문구는 "비가 오고 있어요."만</li>
 *   <li>아니면 이번 비 예보(오늘 · 내일)가 있으면 {@code FORECAST} — 예보 · 마감 · 대신 점검 진행 상황</li>
 *   <li>둘 다 아니면 {@code NONE}</li>
 * </ul>
 * 개인 정보는 넣지 않습니다 (로그인 없이 보는 공개 API).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RainNoticeService {

    private static final String RAINING_MESSAGE = "비가 오고 있어요.";

    private final CurrentWeatherService currentWeatherService;
    private final CurrentRainForecastFinder currentRainForecastFinder;
    private final InspectionRequestRepository inspectionRequestRepository;
    private final Clock clock;

    /**
     * 격자의 지금 배너를 만듭니다.
     *
     * <p>현재 날씨를 기상청에서 바로 받아 올 수 있어(최대 2초) 트랜잭션 밖에서 실행합니다. DB 조회는 각각 짧게 끝나고,
     * 기상청을 기다리는 동안 DB 커넥션을 잡지 않습니다.
     *
     * @param grid 기상청 격자
     * @return 배너
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RainNoticeResponse current(Grid grid) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        WeatherStatus weather = currentWeatherService.status(grid, now);
        Optional<RainForecast> forecast = currentRainForecastFinder.find(grid, today);

        ForecastSummary forecastSummary = forecast.map(this::forecastSummary).orElse(null);
        HelpSummary help = forecast.map(f -> helpSummary(f.getId())).orElse(null);

        if (weather.raining()) {
            return new RainNoticeResponse(State.RAINING, true, weather.observedAt(),
                    forecastSummary, help, RAINING_MESSAGE);
        }
        if (forecast.isPresent()) {
            return new RainNoticeResponse(State.FORECAST, false, weather.observedAt(),
                    forecastSummary, help, forecastMessage(forecast.get(), today));
        }
        return new RainNoticeResponse(State.NONE, false, weather.observedAt(), null, null, null);
    }

    private ForecastSummary forecastSummary(RainForecast forecast) {
        return new ForecastSummary(forecast.getId(), forecast.getRainDate(),
                forecast.getRainStartAt(),
                inspectionRequestRepository.findEarliestCompleteDeadline(
                        forecast.getId(), RequestStatus.IN_PROGRESS),
                forecast.isSimulated());
    }

    private HelpSummary helpSummary(Long forecastId) {
        long needsHelp = 0;
        long inProgress = 0;
        long done = 0;
        for (Object[] row : inspectionRequestRepository.countByStatus(forecastId)) {
            RequestStatus status = (RequestStatus) row[0];
            long count = (Long) row[1];
            switch (status) {
                case OPEN -> needsHelp += count;
                case SENT, ACCEPTED, CLAIMED -> inProgress += count;
                case COMPLETED -> done += count;
                default -> {
                    // MISSED · EXPIRED · CANCELLED는 진행 상황에 넣지 않는다
                }
            }
        }
        return new HelpSummary(needsHelp, inProgress, done);
    }

    private static String forecastMessage(RainForecast forecast, LocalDate today) {
        String day = forecast.getRainDate().equals(today) ? "오늘" : "내일";
        return day + " " + forecast.getRainStartAt().getHour()
                + "시쯤 비 예보가 있어요. 비 오기 전에 빗물받이를 살펴봐 주세요.";
    }
}
