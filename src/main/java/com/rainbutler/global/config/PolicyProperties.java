package com.rainbutler.global.config;

import java.math.BigDecimal;
import java.time.LocalTime;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 서비스 운영 정책 값입니다. ({@code rain-butler.policy})
 *
 * <p>주기·반경·제한 횟수처럼 운영 중 바뀔 수 있는 값은 DB나 코드 상수가 아니라 이 설정으로 관리합니다.
 *
 * @param maxAdoptionsPerUser 사용자당 최대 입양 수
 * @param managementCycleDays 정기 점검 주기 (일)
 * @param winterManagementCycleDays 겨울 주기 선택 시 정기 점검 주기 (일)
 * @param adoptionWarningDays 미관리 경고 알림 기준 (일)
 * @param adoptionReleaseDays 미관리 자동 파양 기준 (일)
 * @param substituteRadiusMeters 대신 점검 대상 반경 (m)
 * @param substituteDailyLimit 1인 하루 대신 점검 최대 횟수
 * @param locationMismatchMeters 촬영 위치 불일치 판정 거리 (m)
 * @param aiConfidenceThreshold AI 인증 통과 최소 확신도
 * @param rainNoticeTime 비 예보 알림 발송 시각
 * @param rainReplyDeadlineTime 비 예보 요청 응답 마감 시각
 * @param restCooldownDays 점검 쉬기 신청 가능 간격 (일, 90일에 한 번)
 */
@ConfigurationProperties(prefix = "rain-butler.policy")
public record PolicyProperties(
        int maxAdoptionsPerUser,
        int managementCycleDays,
        int winterManagementCycleDays,
        int adoptionWarningDays,
        int adoptionReleaseDays,
        int substituteRadiusMeters,
        int substituteDailyLimit,
        int locationMismatchMeters,
        BigDecimal aiConfidenceThreshold,
        LocalTime rainNoticeTime,
        LocalTime rainReplyDeadlineTime,
        int restCooldownDays) {
}
