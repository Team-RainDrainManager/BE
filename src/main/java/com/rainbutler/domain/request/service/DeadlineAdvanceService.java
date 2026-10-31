package com.rainbutler.domain.request.service;

import com.rainbutler.domain.forecast.repository.RainForecastRepository;
import com.rainbutler.domain.notification.entity.NotificationType;
import com.rainbutler.domain.request.entity.InspectionRequest;
import com.rainbutler.domain.request.entity.RequestStatus;
import com.rainbutler.domain.request.repository.InspectionRequestRepository;
import com.rainbutler.global.config.PolicyProperties;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비가 예보보다 일찍 오면 진행 중 요청의 마감을 당깁니다. <b>당기기만 하고 늦추지는 않습니다.</b>
 *
 * <p>비 오는 중에 청소하지 않고 미리 하게 하려는 것입니다. 파일럿은 한 지역이라, 진행 중 예보(실제 · 가상 따로)
 * 중 <b>지금 이후 가장 이른 비 시작</b>을 기준으로 새 완료 마감을 구합니다
 * ({@link RequestSchedulePolicy#completeDeadlineBefore}, 비 시작 1시간 전 · 밤이면 직전 21:00).
 *
 * <ul>
 *   <li>새 완료 마감이 지금 완료 마감보다 이른 진행 중 요청만 바꾼다. 응답 마감이 더 늦으면 같이 당긴다</li>
 *   <li>관련자에게 {@code REQUEST_DEADLINE_CHANGED} 알림</li>
 *   <li>새 완료 마감까지 {@code min-remaining-minutes-after-advance}(30분) 이하만 남으면 그 요청은 취소하고
 *       "비 올 때는 청소하지 마세요" 알림 (안전 우선, 불이익 없음)</li>
 * </ul>
 * 예: 내일 비 요청(완료 마감 오늘 20:00) 중 오늘 18:00 비가 새로 확정 → 완료 마감 17:00로 당김.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeadlineAdvanceService {

    private final RainForecastRepository rainForecastRepository;
    private final InspectionRequestRepository inspectionRequestRepository;
    private final RequestSchedulePolicy schedulePolicy;
    private final RequestNotifier requestNotifier;
    private final PolicyProperties policy;
    private final Clock clock;

    /**
     * 실제 또는 가상 예보 기준으로 진행 중 요청의 마감을 당깁니다.
     *
     * @param simulated 가상 예보 기준이면 true (가상 예보의 요청만 대상)
     * @return 마감을 당기거나 취소한 요청 수
     */
    @Transactional
    public int advance(boolean simulated) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime earliestRain =
                rainForecastRepository.findEarliestActiveRainStartAfter(simulated, now);
        if (earliestRain == null) {
            return 0;
        }
        LocalDateTime newDeadline = schedulePolicy.completeDeadlineBefore(earliestRain);
        List<InspectionRequest> requests = inspectionRequestRepository
                .findInProgressDeadlineAfter(simulated, RequestStatus.IN_PROGRESS, newDeadline);
        boolean tooLate = !newDeadline.isAfter(
                now.plusMinutes(policy.minRemainingMinutesAfterAdvance()));

        for (InspectionRequest request : requests) {
            if (tooLate) {
                request.cancel(now);
                requestNotifier.notifyRelated(request, NotificationType.REQUEST_CANCELLED,
                        "비가 일찍 와서 이번 점검은 쉬어요",
                        "비 올 때는 청소하지 마세요. 이번 점검은 하지 않아도 되고 불이익은 없어요.");
            } else {
                request.advanceDeadline(newDeadline);
                requestNotifier.notifyRelated(request, NotificationType.REQUEST_DEADLINE_CHANGED,
                        "점검 마감이 당겨졌어요", "비가 예보보다 일찍 올 것 같아요. "
                                + DeadlineText.of(newDeadline, now.toLocalDate())
                                + "까지 살펴봐 주세요.");
            }
        }
        if (!requests.isEmpty()) {
            log.info("[요청 마감 당기기] 비 시작 {} → 완료 마감 {} : 요청 {}건 {}", earliestRain,
                    newDeadline, requests.size(), tooLate ? "취소" : "당김");
        }
        return requests.size();
    }
}
