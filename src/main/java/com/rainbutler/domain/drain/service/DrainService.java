package com.rainbutler.domain.drain.service;

import com.rainbutler.domain.drain.dto.Response.DrainResponse;
import com.rainbutler.domain.drain.entity.Drain;
import com.rainbutler.domain.drain.repository.DrainRepository;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DrainService {

    private final DrainRepository drainRepository;

    /**
     * 지도 영역 내 빗물받이 목록 조회
     */
    public List<DrainResponse> getDrainList(
            Double minLatitude,
            Double maxLatitude,
            Double minLongitude,
            Double maxLongitude
    ) {
        // 1. 위도 범위 검증: -90 ~ 90
        if (minLatitude < -90 || minLatitude > 90
                || maxLatitude < -90 || maxLatitude > 90) {
            throw new BusinessException(
                    ErrorCode.LOCATION_INVALID_INPUT,
                    "위도는 -90 이상 90 이하여야 합니다."
            );
        }
        // 2. 경도 범위 검증: -180 ~ 180
        if (minLongitude < -180 || minLongitude > 180
                || maxLongitude < -180 || maxLongitude > 180) {
            throw new BusinessException(
                    ErrorCode.LOCATION_INVALID_INPUT,
                    "경도는 -180 이상 180 이하여야 합니다."
            );
        }

        // 3. 최소값이 최댓값보다 큰 경우 검증
        if (minLatitude > maxLatitude) {
            throw new BusinessException(
                    ErrorCode.LOCATION_INVALID_INPUT,
                    "최소 위도는 최대 위도보다 클 수 없습니다."
            );
        }
        if (minLongitude > maxLongitude) {
            throw new BusinessException(
                    ErrorCode.LOCATION_INVALID_INPUT,
                    "최소 경도는 최대 경도보다 클 수 없습니다."
            );
        }
        List<Drain> drains =
                drainRepository.findByLatitudeBetweenAndLongitudeBetween(
                        minLatitude,
                        maxLatitude,
                        minLongitude,
                        maxLongitude
                );

        return drains.stream()
                .map(drain -> DrainResponse.builder()
                        .id(drain.getId())
                        .latitude(drain.getLatitude())
                        .longitude(drain.getLongitude())
                        .adoptionStatus(drain.getAdoptionStatus())
                        .isRisk(drain.isRisk())
                        .build())
                .toList();
    }
}