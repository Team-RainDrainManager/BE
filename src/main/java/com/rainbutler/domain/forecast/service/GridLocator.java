package com.rainbutler.domain.forecast.service;

import com.rainbutler.domain.forecast.client.KmaProperties;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.geo.KmaGridConverter;
import com.rainbutler.global.geo.KmaGridConverter.Grid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * API로 받은 좌표(선택)를 기상청 격자로 바꿉니다. 좌표가 없으면 기본 격자(파일럿 역곡동)입니다.
 * (배너 · 가상 예보 · 시연용 비 API)
 */
@Component
@RequiredArgsConstructor
public class GridLocator {

    private final KmaProperties kmaProperties;

    /**
     * 좌표의 격자를 찾습니다.
     *
     * @param latitude 위도 (선택)
     * @param longitude 경도 (선택)
     * @return 격자 (둘 다 없으면 기본 격자)
     * @throws BusinessException 위도 · 경도 중 하나만 보냄 (INVALID_INPUT)
     */
    public Grid locate(Double latitude, Double longitude) {
        if (latitude == null && longitude == null) {
            return kmaProperties.defaultGrid();
        }
        if (latitude == null || longitude == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "위도와 경도를 함께 보내 주세요.");
        }
        return KmaGridConverter.toGrid(latitude, longitude);
    }
}
