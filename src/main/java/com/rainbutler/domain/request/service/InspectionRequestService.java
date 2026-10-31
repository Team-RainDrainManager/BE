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
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
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

    /**
     * 이 사용자가 지금 이 요청을 완료할 수 있는지 확인합니다. (점검 가능 여부 화면 · 점검 제출 전 검사)
     *
     * <ul>
     *   <li>입양자: SENT · ACCEPTED (응답하지 않고 바로 점검해도 인정)</li>
     *   <li>대신 점검 수락자: CLAIMED이고 본인이 맡은 요청</li>
     *   <li>어느 쪽이든 완료 마감 전</li>
     * </ul>
     *
     * @param requestId 요청 id
     * @param userId 점검한 사용자
     * @param now 현재 시각
     * @return 완료할 수 있으면 true (없는 요청이면 false)
     */
    @Transactional(readOnly = true)
    public boolean canComplete(Long requestId, Long userId, LocalDateTime now) {
        return inspectionRequestRepository.findById(requestId)
                .map(request -> isCompletable(request, userId, now))
                .orElse(false);
    }

    /**
     * 점검 제출에 맞춰 요청을 완료합니다. 점검 기록을 저장하는 트랜잭션 안에서 부르세요.
     *
     * @param requestId 요청 id
     * @param userId 점검한 사용자
     * @param now 완료 시각
     * @throws BusinessException 완료할 수 없음 (409 REQUEST_NOT_COMPLETABLE), 없는 요청 (404)
     */
    @Transactional
    public void complete(Long requestId, Long userId, LocalDateTime now) {
        InspectionRequest request = inspectionRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REQUEST_NOT_FOUND));
        if (!isCompletable(request, userId, now)) {
            throw new BusinessException(ErrorCode.REQUEST_NOT_COMPLETABLE);
        }
        request.complete(now);
        log.info("[비 예보 요청] 완료 requestId={} userId={}", requestId, userId);
    }

    private boolean isCompletable(InspectionRequest request, Long userId, LocalDateTime now) {
        if (!now.isBefore(request.getCompleteDeadlineAt())) {
            return false;
        }
        RequestStatus status = request.getStatus();
        boolean owner = request.getAdoption() != null
                && request.getAdoption().getUser().getId().equals(userId);
        if (owner && (status == RequestStatus.SENT || status == RequestStatus.ACCEPTED)) {
            return true;
        }
        return status == RequestStatus.CLAIMED && request.getSubstituteUser() != null
                && request.getSubstituteUser().getId().equals(userId);
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
