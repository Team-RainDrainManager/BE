package com.rainbutler.domain.drain.dto.Request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 빗물받이 생성 요청입니다.
 *
 * <p>위치(촬영 위치 그대로)와 현장 사진은 필수입니다. 더러움 단계와 위험 여부는 서버 AI가
 * 사진으로 판정하므로, {@code isRisk}는 사용자 의견(체크 여부)일 뿐 저장값이 아닙니다.
 * 사용자 체크값과 AI 판정이 다르면 응답의 리마인드로 알려주고 관리자 검토용 제보를 함께 만듭니다.
 */
public record DrainCreateRequest(
        @NotNull(message = "위도를 입력해 주세요.")
        @DecimalMin(value = "-90.0", message = "위도는 -90 이상 90 이하여야 합니다.")
        @DecimalMax(value = "90.0", message = "위도는 -90 이상 90 이하여야 합니다.")
        Double latitude,

        @NotNull(message = "경도를 입력해 주세요.")
        @DecimalMin(value = "-180.0", message = "경도는 -180 이상 180 이하여야 합니다.")
        @DecimalMax(value = "180.0", message = "경도는 -180 이상 180 이하여야 합니다.")
        Double longitude,

        @NotBlank(message = "현장 사진을 등록해 주세요.")
        String photoUrl,

        /**
         * 사용자가 직접 체크한 위험 여부입니다. null이면 체크하지 않은 것입니다.
         * 실제 저장값은 AI 판정을 따릅니다.
         */
        Boolean isRisk,

        @Size(max = 500, message = "위험 사유는 500자 이내로 입력해 주세요.")
        String riskDescription
) {
}
