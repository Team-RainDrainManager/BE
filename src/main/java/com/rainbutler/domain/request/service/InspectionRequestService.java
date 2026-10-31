package com.rainbutler.domain.request.service;

import com.rainbutler.domain.adoption.entity.Adoption;
import com.rainbutler.domain.adoption.repository.AdoptionRepository;
import com.rainbutler.domain.drain.entity.Drain;
import com.rainbutler.domain.forecast.entity.RainForecast;
import com.rainbutler.domain.forecast.repository.RainForecastRepository;
import com.rainbutler.domain.notification.entity.NotificationType;
import com.rainbutler.domain.push.service.PushService;
import com.rainbutler.domain.request.entity.InspectionRequest;
import com.rainbutler.domain.request.entity.RequestStatus;
import com.rainbutler.domain.request.repository.InspectionRequestRepository;
import com.rainbutler.global.util.KoreanJosa;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비 예보 점검 요청을 만들어 보내고, 예보가 취소되면 요청을 취소합니다.
 *
 * <p>발송 대상 (운영 중 빗물받이)
 * <ol>
 *   <li>ACTIVE 입양이 있는 빗물받이 → {@code SENT}, 입양자에게 알림. 입양자가 점검 쉬기 중이어도 보낸다</li>
 *   <li>입양되지 않은 위험 빗물받이 → 바로 {@code OPEN}, 근처 입양자에게 대신 점검 알림</li>
 * </ol>
 * 같은 예보로 이미 요청이 있거나, 다른 예보의 요청이 아직 진행 중인 빗물받이는 건너뜁니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InspectionRequestService {

    private final InspectionRequestRepository inspectionRequestRepository;
    private final RainForecastRepository rainForecastRepository;
    private final AdoptionRepository adoptionRepository;
    private final SubstituteNotifier substituteNotifier;
    private final PushService pushService;
    private final RequestNotifier requestNotifier;
    private final Clock clock;

    /**
     * 예보로 점검 요청을 만들고 보냅니다.
     *
     * @param forecastId 예보 id (진행 중이 아니면 아무것도 하지 않는다)
     * @param schedule 발송 시각 · 마감 (SEND_NOW)
     * @return 만든 요청 수
     */
    @Transactional
    public int createForForecast(Long forecastId, RequestSchedule schedule) {
        RainForecast forecast = rainForecastRepository.findById(forecastId).orElse(null);
        if (forecast == null || !forecast.isActive() || !schedule.isSendNow()) {
            return 0;
        }
        Set<Long> blocked = inspectionRequestRepository.findBlockedDrainIds(
                forecastId, RequestStatus.IN_PROGRESS);
        LocalDate today = schedule.sentAt().toLocalDate();
        boolean tomorrow = forecast.getRainDate().isAfter(today);

        int created = 0;
        for (Adoption adoption : adoptionRepository.findAllActiveOnActiveDrains()) {
            if (!blocked.add(adoption.getDrain().getId())) {
                continue;
            }
            InspectionRequest request = inspectionRequestRepository.save(
                    newRequest(forecast, adoption.getDrain(), adoption, RequestStatus.SENT,
                            schedule));
            notifyAdopter(request, adoption, tomorrow, today);
            created++;
        }

        List<InspectionRequest> opened = new ArrayList<>();
        for (Drain drain : inspectionRequestRepository.findUnadoptedRiskDrains()) {
            if (!blocked.add(drain.getId())) {
                continue;
            }
            opened.add(inspectionRequestRepository.save(
                    newRequest(forecast, drain, null, RequestStatus.OPEN, schedule)));
        }
        if (!opened.isEmpty()) {
            substituteNotifier.notifyNearbyAdopters(opened);
        }
        created += opened.size();
        log.info("[비 예보 요청] 예보 id={} 요청 {}건 생성 (입양자 {} · 바로 대신 점검 {})",
                forecastId, created, created - opened.size(), opened.size());
        return created;
    }

    /**
     * 취소된 예보의 진행 중 요청을 취소하고 관련자(입양자, 대신 점검 수락자)에게 알립니다.
     * 이미 완료 · 놓침 처리된 요청은 그대로 둡니다.
     *
     * @param forecastId 취소된 예보 id
     * @return 취소한 요청 수
     */
    @Transactional
    public int cancelForForecast(Long forecastId) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<InspectionRequest> requests = inspectionRequestRepository
                .findAllByForecast_IdAndStatusIn(forecastId, RequestStatus.IN_PROGRESS);
        for (InspectionRequest request : requests) {
            request.cancel(now);
            requestNotifier.notifyRelated(request, NotificationType.REQUEST_CANCELLED,
                    "비 예보가 취소됐어요", "이번 비 예보 점검은 하지 않아도 돼요.");
        }
        log.info("[비 예보 요청] 예보 id={} 취소로 요청 {}건 취소", forecastId, requests.size());
        return requests.size();
    }

    private InspectionRequest newRequest(RainForecast forecast, Drain drain, Adoption adoption,
            RequestStatus status, RequestSchedule schedule) {
        return InspectionRequest.builder()
                .forecast(forecast)
                .drain(drain)
                .adoption(adoption)
                .status(status)
                .sentAt(schedule.sentAt())
                .replyDeadlineAt(schedule.replyDeadlineAt())
                .completeDeadlineAt(schedule.completeDeadlineAt())
                .build();
    }

    private void notifyAdopter(InspectionRequest request, Adoption adoption, boolean tomorrow,
            LocalDate today) {
        NotificationType type =
                tomorrow ? NotificationType.RAIN_TOMORROW : NotificationType.RAIN_TODAY;
        String title = tomorrow ? "내일 비 소식이 있어요 ☔" : "오늘 비 소식이 있어요 ☔";
        String body = KoreanJosa.eulReul(adoption.getNickname()) + " "
                + DeadlineText.of(request.getCompleteDeadlineAt(), today) + "까지 살펴봐 주세요. "
                + DeadlineText.of(request.getReplyDeadlineAt(), today) + "까지 가능 여부를 알려 주세요.";
        pushService.notify(adoption.getUser().getId(), type, title, body, request.getId(),
                adoption.getId(), Map.of("requestId", String.valueOf(request.getId())));
    }
}
