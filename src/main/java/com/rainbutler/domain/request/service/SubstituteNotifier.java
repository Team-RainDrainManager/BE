package com.rainbutler.domain.request.service;

import com.rainbutler.domain.adoption.entity.Adoption;
import com.rainbutler.domain.adoption.repository.AdoptionRepository;
import com.rainbutler.domain.drain.entity.Drain;
import com.rainbutler.domain.notification.entity.NotificationType;
import com.rainbutler.domain.push.service.PushService;
import com.rainbutler.domain.request.entity.InspectionRequest;
import com.rainbutler.domain.sanction.service.SanctionService;
import com.rainbutler.global.config.PolicyProperties;
import com.rainbutler.global.util.GeoDistance;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 대신 점검으로 공개된 요청을 근처 입양자에게 알립니다.
 *
 * <p>사용자 위치는 저장하지 않으므로 <b>요청 빗물받이에서 반경(기본 300m) 안의 빗물받이를 입양한 사람</b>에게
 * 보냅니다. "내 또랑이 돌보러 가는 길에 같이 봐 주세요"라는 취지입니다.
 *
 * <ul>
 *   <li>1차: 위경도 사각형으로 후보 입양 조회 → 2차: 하버사인 거리로 반경 안만</li>
 *   <li>제외: 그 요청의 원래 입양자, 제재 중인 사용자</li>
 *   <li>한 번에 여러 요청이 공개되면 사용자별로 묶어 알림 1개</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubstituteNotifier {

    private final AdoptionRepository adoptionRepository;
    private final SanctionService sanctionService;
    private final PushService pushService;
    private final PolicyProperties policy;
    private final Clock clock;

    /**
     * 공개된 요청들을 근처 입양자에게 알립니다. 호출하는 쪽 트랜잭션 안에서 부르세요.
     *
     * @param requests 대신 점검으로 공개된 요청
     * @return 알림을 받은 사용자 수
     */
    public int notifyNearbyAdopters(List<InspectionRequest> requests) {
        int radius = policy.substituteRadiusMeters();
        Map<Long, Recipient> recipients = new LinkedHashMap<>();
        for (InspectionRequest request : requests) {
            Drain drain = request.getDrain();
            Long originalAdopterId = request.getAdoption() == null
                    ? null : request.getAdoption().getUser().getId();
            double latDelta = GeoDistance.latDelta(radius);
            double lngDelta = GeoDistance.lngDelta(drain.getLatitude(), radius);
            List<Adoption> candidates = adoptionRepository.findActiveInBox(
                    drain.getLatitude() - latDelta, drain.getLatitude() + latDelta,
                    drain.getLongitude() - lngDelta, drain.getLongitude() + lngDelta);
            for (Adoption adoption : candidates) {
                Long userId = adoption.getUser().getId();
                if (Objects.equals(userId, originalAdopterId)
                        || adoption.getDrain().getId().equals(drain.getId())) {
                    continue;
                }
                double distance = GeoDistance.meters(drain.getLatitude(), drain.getLongitude(),
                        adoption.getDrain().getLatitude(), adoption.getDrain().getLongitude());
                if (distance > radius) {
                    continue;
                }
                recipients.computeIfAbsent(userId, id -> new Recipient())
                        .add(request, adoption, distance);
            }
        }

        LocalDate today = LocalDateTime.now(clock).toLocalDate();
        int notified = 0;
        for (Map.Entry<Long, Recipient> entry : recipients.entrySet()) {
            if (sanctionService.isSanctioned(entry.getKey())) {
                continue;
            }
            Recipient recipient = entry.getValue();
            InspectionRequest first = recipient.requests.get(0);
            LocalDateTime deadline = recipient.requests.stream()
                    .map(InspectionRequest::getCompleteDeadlineAt)
                    .min(Comparator.naturalOrder())
                    .orElseThrow();
            int count = recipient.requests.size();
            String title = count == 1
                    ? "근처 빗물받이가 도움이 필요해요"
                    : "근처 또랑이 " + count + "곳이 도움이 필요해요";
            String body = recipient.nearestNickname + " 돌보러 가는 길에 근처 빗물받이도 같이 봐 주세요. "
                    + DeadlineText.of(deadline, today) + "까지예요.";
            Long notificationId = pushService.notify(entry.getKey(),
                    NotificationType.SUBSTITUTE_OPEN, title, body, first.getId(), null,
                    Map.of("requestId", String.valueOf(first.getId())));
            if (notificationId != null) {
                notified++;
            }
        }
        log.info("[대신 점검 알림] 요청 {}건 → 근처 입양자 {}명", requests.size(), notified);
        return notified;
    }

    /** 한 사용자에게 보낼 요청 묶음과 가장 가까운 내 또랑이 이름입니다. */
    private static final class Recipient {
        private final List<InspectionRequest> requests = new ArrayList<>();
        private String nearestNickname;
        private double nearestDistance = Double.MAX_VALUE;

        private void add(InspectionRequest request, Adoption adoption, double distance) {
            if (!requests.contains(request)) {
                requests.add(request);
            }
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestNickname = adoption.getNickname();
            }
        }
    }
}
