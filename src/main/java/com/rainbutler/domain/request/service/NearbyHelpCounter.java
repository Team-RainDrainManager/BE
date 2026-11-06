package com.rainbutler.domain.request.service;

import com.rainbutler.domain.drain.entity.Drain;
import com.rainbutler.domain.request.repository.InspectionRequestRepository;
import com.rainbutler.domain.request.repository.OpenDrainPoint;
import com.rainbutler.global.config.PolicyProperties;
import com.rainbutler.global.util.GeoDistance;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 빗물받이 근처(기본 100m)에서 지금 대신 점검이 필요한 빗물받이 수를 셉니다.
 *
 * <p>대신 점검 개별 알림을 보내지 않는 대신, 입양자가 "가능"으로 응답했을 때와 내 요청 목록에서 이 개수를 보여 줘
 * 지도에서 자발적으로 찾아가게 합니다.
 *
 * <ul>
 *   <li>대상: 완료 마감 전 OPEN 요청의 빗물받이 (예보와 관계없이 지금 도움이 필요한 것 전부)</li>
 *   <li>기준 빗물받이 자신은 빼고, 같은 빗물받이에 요청이 여러 개여도 1개로 센다</li>
 *   <li>여러 기준을 한 번에 셀 때는 기준들을 덮는 사각형으로 <b>한 번만</b> 조회하고 거리는 메모리에서 계산한다
 *       (요청 목록에 내 요청이 여러 개여도 쿼리 1번)</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class NearbyHelpCounter {

    private final InspectionRequestRepository inspectionRequestRepository;
    private final PolicyProperties policy;

    /**
     * 빗물받이 한 곳 근처의 도움 필요 개수를 셉니다.
     *
     * @param center 기준 빗물받이
     * @param now 현재 시각
     * @return 반경 안 도움 필요 빗물받이 수
     */
    public int countOpenNear(Drain center, LocalDateTime now) {
        return countOpenNear(List.of(center), now).getOrDefault(center.getId(), 0);
    }

    /**
     * 여러 빗물받이 근처의 도움 필요 개수를 한 번의 조회로 셉니다.
     *
     * @param centers 기준 빗물받이들
     * @param now 현재 시각
     * @return 기준 빗물받이 id → 개수
     */
    public Map<Long, Integer> countOpenNear(Collection<Drain> centers, LocalDateTime now) {
        Map<Long, Integer> counts = new LinkedHashMap<>();
        if (centers.isEmpty()) {
            return counts;
        }
        int radius = policy.nearbyHelpRadiusMeters();
        double minLat = centers.stream().mapToDouble(Drain::getLatitude).min().orElseThrow();
        double maxLat = centers.stream().mapToDouble(Drain::getLatitude).max().orElseThrow();
        double minLng = centers.stream().mapToDouble(Drain::getLongitude).min().orElseThrow();
        double maxLng = centers.stream().mapToDouble(Drain::getLongitude).max().orElseThrow();
        double latDelta = GeoDistance.latDelta(radius);
        double lngDelta = GeoDistance.lngDelta(Math.max(Math.abs(minLat), Math.abs(maxLat)),
                radius);
        List<OpenDrainPoint> open = inspectionRequestRepository.findOpenDrainsInBox(now,
                minLat - latDelta, maxLat + latDelta, minLng - lngDelta, maxLng + lngDelta);

        for (Drain center : centers) {
            int count = (int) open.stream()
                    .filter(drain -> !drain.drainId().equals(center.getId()))
                    .filter(drain -> GeoDistance.meters(center.getLatitude(),
                            center.getLongitude(), drain.latitude(), drain.longitude()) <= radius)
                    .count();
            counts.put(center.getId(), count);
        }
        return counts;
    }
}
