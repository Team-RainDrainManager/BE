package com.rainbutler.domain.request.service;

import com.rainbutler.domain.request.dto.RainRequestStatus;
import com.rainbutler.domain.request.dto.RainRequestSummary;
import com.rainbutler.domain.request.entity.InspectionRequest;
import com.rainbutler.domain.request.entity.RequestStatus;
import com.rainbutler.domain.request.repository.InspectionRequestRepository;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 지도 빗물받이들의 이번 비 대신 점검 상태를 찾습니다.
 *
 * <p>격자마다 예보가 달라 "전체의 이번 비 예보" 하나로 고를 수 없으므로, 빗물받이마다 자기 요청으로 판단합니다.
 * <ul>
 *   <li>진행 중 요청(SENT · ACCEPTED · OPEN · CLAIMED)이 있으면 그것</li>
 *   <li>없으면 오늘 완료한 요청</li>
 *   <li>둘 다 없으면 상태 없음</li>
 * </ul>
 * 같은 순위가 여럿이면 최근 발송한 요청을 씁니다.
 */
@Component
@RequiredArgsConstructor
public class RainRequestLookup {

    /** 진행 중 요청을 오늘 완료한 요청보다 먼저, 같은 순위면 최근 발송 순 */
    private static final Comparator<InspectionRequest> PRIORITY = Comparator
            .comparing((InspectionRequest request) -> !request.isInProgress())
            .thenComparing(InspectionRequest::getSentAt, Comparator.reverseOrder())
            .thenComparing(InspectionRequest::getId, Comparator.reverseOrder());

    private final InspectionRequestRepository inspectionRequestRepository;

    /**
     * 빗물받이들의 이번 비 요청 상태를 한 번의 조회로 찾습니다.
     *
     * @param drainIds 빗물받이 id
     * @param today 오늘 (이날 완료한 요청까지 보여 준다)
     * @return 빗물받이 id → 요청 요약 (보여 줄 요청이 없으면 빠짐)
     */
    public Map<Long, RainRequestSummary> findForDrains(Collection<Long> drainIds, LocalDate today) {
        Map<Long, InspectionRequest> picked = new HashMap<>();
        if (drainIds.isEmpty()) {
            return Map.of();
        }
        for (InspectionRequest request : inspectionRequestRepository.findForMap(
                drainIds, RequestStatus.IN_PROGRESS, today.atStartOfDay())) {
            picked.merge(request.getDrain().getId(), request,
                    (current, candidate) -> PRIORITY.compare(candidate, current) < 0
                            ? candidate : current);
        }
        Map<Long, RainRequestSummary> result = new HashMap<>();
        picked.forEach((drainId, request) -> result.put(drainId, new RainRequestSummary(
                request.getId(), RainRequestStatus.of(request.getStatus()))));
        return result;
    }
}
