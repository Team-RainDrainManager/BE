package com.rainbutler.domain.request.service;

import com.rainbutler.domain.adoption.entity.Adoption;
import com.rainbutler.domain.adoption.entity.AdoptionStatus;
import com.rainbutler.domain.adoption.repository.AdoptionRepository;
import com.rainbutler.domain.drain.entity.Drain;
import com.rainbutler.domain.request.dto.MyRequestResponse;
import com.rainbutler.domain.request.dto.MyRequestResponse.Action;
import com.rainbutler.domain.request.dto.MyRequestResponse.DrainSummary;
import com.rainbutler.domain.request.dto.MyRequestResponse.Role;
import com.rainbutler.domain.request.entity.InspectionRequest;
import com.rainbutler.domain.request.entity.RequestStatus;
import com.rainbutler.domain.request.repository.InspectionRequestRepository;
import com.rainbutler.global.config.PolicyProperties;
import com.rainbutler.global.util.GeoDistance;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 비 예보 요청 목록을 만듭니다.
 *
 * <ul>
 *   <li>OWNER: 내 ACTIVE 입양의 요청</li>
 *   <li>CLAIMANT: 내가 대신 점검으로 맡은 요청</li>
 *   <li>NEARBY: 공개(OPEN) 요청 중 보낸 위치에서 300m 안, 또는 내 또랑이에서 300m 안. 내 또랑이 요청은 제외</li>
 * </ul>
 * 진행 중인 요청과 최근 24시간 안에 끝난(완료 · 놓침) 요청을 보여 주고, 취소된 요청은 뺍니다.
 * 진행 중인 요청을 다음 마감이 임박한 순으로, 그다음 끝난 요청을 보여 줍니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyRequestQueryService {

    private static final Duration RECENT = Duration.ofHours(24);

    private final InspectionRequestRepository inspectionRequestRepository;
    private final AdoptionRepository adoptionRepository;
    private final PolicyProperties policy;
    private final Clock clock;

    /**
     * 내 요청 목록을 조회합니다.
     *
     * @param userId 로그인 사용자
     * @param latitude 앱 사용 중 현재 위도 (선택, 저장하지 않음)
     * @param longitude 앱 사용 중 현재 경도 (선택, 저장하지 않음)
     * @return 요청 목록
     */
    public List<MyRequestResponse> findMine(Long userId, Double latitude, Double longitude) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime since = now.minus(RECENT);
        Map<Long, MyRequestResponse> result = new LinkedHashMap<>();

        for (InspectionRequest request : inspectionRequestRepository.findMineAsOwner(
                userId, RequestStatus.IN_PROGRESS, since)) {
            result.putIfAbsent(request.getId(),
                    toResponse(request, Role.OWNER, now, latitude, longitude));
        }
        for (InspectionRequest request : inspectionRequestRepository.findMineAsClaimant(
                userId, RequestStatus.IN_PROGRESS, since)) {
            result.putIfAbsent(request.getId(),
                    toResponse(request, Role.CLAIMANT, now, latitude, longitude));
        }

        List<Drain> myDrains = adoptionRepository
                .findAllByUser_IdAndStatus(userId, AdoptionStatus.ACTIVE).stream()
                .map(Adoption::getDrain)
                .toList();
        int radius = policy.substituteRadiusMeters();
        for (InspectionRequest request : inspectionRequestRepository.findOpenBefore(now)) {
            if (result.containsKey(request.getId()) || isOwnedBy(request, userId)) {
                continue;
            }
            Drain drain = request.getDrain();
            boolean nearMe = latitude != null && longitude != null
                    && distance(latitude, longitude, drain) <= radius;
            boolean nearMyDrain = myDrains.stream().anyMatch(mine ->
                    distance(mine.getLatitude(), mine.getLongitude(), drain) <= radius);
            if (nearMe || nearMyDrain) {
                result.put(request.getId(),
                        toResponse(request, Role.NEARBY, now, latitude, longitude));
            }
        }

        List<MyRequestResponse> list = new ArrayList<>(result.values());
        list.sort(Comparator
                .comparing((MyRequestResponse r) -> r.remainingMinutes() == null)
                .thenComparing(MyRequestQueryService::nextDeadline));
        return list;
    }

    private MyRequestResponse toResponse(InspectionRequest request, Role role,
            LocalDateTime now, Double latitude, Double longitude) {
        Drain drain = request.getDrain();
        String name = request.getAdoption() == null ? null : request.getAdoption().getNickname();
        boolean finished = !request.isInProgress();
        boolean replyOpen = request.getStatus() == RequestStatus.SENT
                && now.isBefore(request.getReplyDeadlineAt());
        LocalDateTime next = replyOpen
                ? request.getReplyDeadlineAt() : request.getCompleteDeadlineAt();
        Long remaining = finished ? null : Math.max(0, Duration.between(now, next).toMinutes());
        Integer distance = latitude == null || longitude == null
                ? null : (int) Math.round(distance(latitude, longitude, drain));
        return new MyRequestResponse(
                request.getId(),
                new DrainSummary(drain.getId(), name, drain.getLatitude(), drain.getLongitude()),
                request.getStatus(),
                role,
                request.getForecast().getRainStartAt(),
                request.getReplyDeadlineAt(),
                request.getCompleteDeadlineAt(),
                remaining,
                distance,
                actions(request, role, now, replyOpen));
    }

    private List<Action> actions(InspectionRequest request, Role role, LocalDateTime now,
            boolean replyOpen) {
        if (!now.isBefore(request.getCompleteDeadlineAt())) {
            return List.of();
        }
        RequestStatus status = request.getStatus();
        return switch (role) {
            case OWNER -> {
                if (status == RequestStatus.SENT) {
                    yield replyOpen
                            ? List.of(Action.REPLY, Action.INSPECT) : List.of(Action.INSPECT);
                }
                yield status == RequestStatus.ACCEPTED ? List.of(Action.INSPECT) : List.of();
            }
            case CLAIMANT -> status == RequestStatus.CLAIMED ? List.of(Action.INSPECT) : List.of();
            case NEARBY -> status == RequestStatus.OPEN ? List.of(Action.CLAIM) : List.of();
        };
    }

    private static LocalDateTime nextDeadline(MyRequestResponse response) {
        boolean replyOpen = response.availableActions().contains(Action.REPLY);
        return replyOpen ? response.replyDeadlineAt() : response.deadlineAt();
    }

    private static boolean isOwnedBy(InspectionRequest request, Long userId) {
        return request.getAdoption() != null
                && Objects.equals(request.getAdoption().getUser().getId(), userId);
    }

    private static double distance(double latitude, double longitude, Drain drain) {
        return GeoDistance.meters(latitude, longitude, drain.getLatitude(), drain.getLongitude());
    }
}
