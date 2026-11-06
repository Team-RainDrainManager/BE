package com.rainbutler.domain.user.certificate;

import java.time.LocalDate;

/**
 * 활동 확인서에 들어가는 값입니다.
 *
 * @param realName 실명
 * @param periodStart 첫 인정 활동일
 * @param issuedAt 발급일
 * @param inspectionCount 인정 점검 수 (정기 + 비 예보 + 대신)
 * @param regularCount 정기 점검 수
 * @param forecastCount 비 예보 점검 수
 * @param substituteCount 대신 점검 수
 * @param volunteerMinutes 인정 봉사 시간 (분)
 */
public record CertificateData(
        String realName,
        LocalDate periodStart,
        LocalDate issuedAt,
        long inspectionCount,
        long regularCount,
        long forecastCount,
        long substituteCount,
        long volunteerMinutes) {
}
