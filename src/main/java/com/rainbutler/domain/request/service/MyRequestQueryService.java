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
import java.util.Set;
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
 * 진행 중인 요청과 최근 24시간 안에 끝난(완료 · 놓침 · 종료) 요청을 보여 주고, 취소된 요청은 뺍니다.
 * 순서: 내 요청 · 맡은 요청(진행 중, 마감 임박순) → 근처 요청(가까운 순, 최대 30개) → 끝난 요청.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyRequestQueryService {

    private static final Duration RECENT = Duration.ofHours(24);

    private final InspectionRequestRepository inspectionRequestRepository;
    private final AdoptionRepository adoptionRepository;
    private final NearbyHelpCounter nearbyHelpCounter;
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

        List<InspectionRequest> owned = inspectionRequestRepository.findMineAsOwner(
                userId, RequestStatus.IN_PROGRESS, since);
        // 진행 중인 내 또랑이 요청의 근처 도움 개수를 한 번의 조회로 센다
        Map<Long, Integer> nearbyCounts = nearbyHelpCounter.countOpenNear(owned.stream()
                .filter(request -> request.getStatus() == RequestStatus.SENT
                        || request.getStatus() == RequestStatus.ACCEPTED)
                .map(InspectionRequest::getDrain)
                .distinct()
                .toList(), now);
        for (InspectionRequest request : owned) {
            result.putIfAbsent(request.getId(), toResponse(request, Role.OWNER, now, latitude,
                    longitude, nearbyCounts.get(request.getDrain().getId())));
        }
        for (InspectionRequest request : inspectionRequestRepository.findMineAsClaimant(
                userId, RequestStatus.IN_PROGRESS, since)) {
            result.putIfAbsent(request.getId(),
                    toResponse(request, Role.CLAIMANT, now, latitude, longitude, null));
        }

        List<MyRequestResponse> mine = new ArrayList<>(result.values());
        List<MyRequestResponse> active = mine.stream()
                .filter(r -> r.remainingMinutes() != null)
                .sorted(Comparator.comparing(MyRequestQueryService::nextDeadline))
                .toList();
        List<MyRequestResponse> finished = mine.stream()
                .filter(r -> r.remainingMinutes() == null)
                .sorted(Comparator.comparing(MyRequestQueryService::nextDeadline))
                .toList();

        List<MyRequestResponse> list = new ArrayList<>(active);
        list.addAll(findNearby(userId, latitude, longitude, now, result.keySet()));
        list.addAll(finished);
        return list;
    }

    /**
     * 근처 공개 요청을 가까운 순으로 최대 {@code nearby-request-list-limit}개 찾습니다.
     *
     * <p>기준점은 보낸 위치와 내 또랑이들입니다. 기준점들을 덮는 사각형으로 DB에서 후보를 거른 뒤, 기준점 중 가장
     * 가까운 곳과의 거리가 반경(300m) 안인 것만 남깁니다.
     */
    private List<MyRequestResponse> findNearby(Long userId, Double latitude, Double longitude,
            LocalDateTime now, Set<Long> alreadyListed) {
        List<double[]> centers = new ArrayList<>();
        if (latitude != null && longitude != null) {
            centers.add(new double[] {latitude, longitude});
        }
        adoptionRepository.findAllByUser_IdAndStatus(userId, AdoptionStatus.ACTIVE).stream()
                .map(Adoption::getDrain)
                .forEach(drain -> centers.add(
                        new double[] {drain.getLatitude(), drain.getLongitude()}));
        if (centers.isEmpty()) {
            return List.of();
        }

        int radius = policy.substituteRadiusMeters();
        double minLat = centers.stream().mapToDouble(c -> c[0]).min().orElseThrow();
        double maxLat = centers.stream().mapToDouble(c -> c[0]).max().orElseThrow();
        double minLng = centers.stream().mapToDouble(c -> c[1]).min().orElseThrow();
        double maxLng = centers.stream().mapToDouble(c -> c[1]).max().orElseThrow();
        double latDelta = GeoDistance.latDelta(radius);
        double lngDelta = GeoDistance.lngDelta(Math.max(Math.abs(minLat), Math.abs(maxLat)),
                radius);
        List<InspectionRequest> candidates = inspectionRequestRepository.findOpenInBox(now,
                minLat - latDelta, maxLat + latDelta, minLng - lngDelta, maxLng + lngDelta);

        record Near(InspectionRequest request, double distance) {
        }
        return candidates.stream()
                .filter(request -> !alreadyListed.contains(request.getId())
                        && !isOwnedBy(request, userId))
                .map(request -> new Near(request, centers.stream()
                        .mapToDouble(c -> distance(c[0], c[1], request.getDrain()))
                        .min().orElseThrow()))
                .filter(near -> near.distance() <= radius)
                .sorted(Comparator.comparingDouble(Near::distance))
                .limit(policy.nearbyRequestListLimit())
                .map(near -> toResponse(
                        near.request(), Role.NEARBY, now, latitude, longitude, null))
                .toList();
    }

    private MyRequestResponse toResponse(InspectionRequest request, Role role,
            LocalDateTime now, Double latitude, Double longitude, Integer nearbyOpenCount) {
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
                actions(request, role, now, replyOpen),
                nearbyOpenCount);
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
