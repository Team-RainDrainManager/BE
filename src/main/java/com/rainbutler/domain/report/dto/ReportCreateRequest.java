package com.rainbutler.domain.report.dto;

import com.rainbutler.domain.report.entity.ReportType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 제보 생성 요청입니다.
 *
 * <p>제보 종류에 따라 필요한 값이 다릅니다.
 *
 * <ul>
 *   <li>{@code ODOR}: 사진({@code photoKey}) 또는 설명({@code description}) 중 하나 필수</li>
 *   <li>{@code REMOVAL}: 사진 · 설명 모두 필수</li>
 *   <li>{@code LOCATION_FIX}: 사진 · 새 위치({@code newLatitude}, {@code newLongitude}) 필수</li>
 *   <li>{@code BLOCKAGE}: 사진 · {@code drainId} 필수. AI가 청소 불가(막힘(3등급) 또는
 *       {@code needsReport=true})로 판정해야 접수되고, 접수되면 {@code blocksAdoption=true}로
 *       입양을 막는다</li>
 *   <li>{@code NEW_DRAIN}: 사진 · 촬영 위치 필수, {@code drainId}는 보내지 않음 (새 빗물받이라 대상이 없음)</li>
 * </ul>
 *
 * @param drainId 대상 빗물받이 id (NEW_DRAIN은 null)
 * @param type 제보 종류 (BLOCKAGE, NEW_DRAIN, LOCATION_FIX, REMOVAL, ODOR)
 * @param photoKey 제보 사진 key (S3 presigned URL로 업로드 후 받은 key)
 * @param description 제보 설명 (최대 1000자)
 * @param latitude 제보 촬영 위도 (NEW_DRAIN은 필수, 나머지는 없으면 빗물받이 위치)
 * @param longitude 제보 촬영 경도 (NEW_DRAIN은 필수, 나머지는 없으면 빗물받이 위치)
 * @param newLatitude LOCATION_FIX인 경우 새 위치 위도
 * @param newLongitude LOCATION_FIX인 경우 새 위치 경도
 */
public record ReportCreateRequest(
        Long drainId,
        @NotNull ReportType type,
        @Size(max = 500) String photoKey,
        @Size(max = 1000) String description,
        @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @DecimalMin("-90.0") @DecimalMax("90.0") Double newLatitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") Double newLongitude
) {
}
