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
 * @param rainNoticeTime 내일 비 예보 요청 일괄 발송 시각 (06:00)
 * @param rainReplyDeadlineTime 비 예보 요청 응답 마감 시각 (12:00). 예전에는 전날 20:00 응답 마감이었으나
 *     기획 변경으로 발송 당일 12:00으로 바뀌었고, 20:00은 완료 마감({@code rainCompleteDeadlineTime})이 됐다
 * @param restCooldownDays 점검 쉬기 신청 가능 간격 (일, 90일에 한 번)
 * @param nightPushStartTime 야간 푸시 시간대 시작 (이 시각부터 야간 동의자에게만 발송)
 * @param nightPushEndTime 야간 푸시 시간대 끝 (이 시각 전까지 야간)
 * @param sanctionFirstDays 허위 제보 1회째 제재 일수
 * @param sanctionSecondDays 허위 제보 2회째 제재 일수
 * @param sanctionPermanentCount 이 횟수째부터 영구 제재
 * @param pauseDays 점검 쉬기 기간 (일)
 * @param volunteerMinutesPerCleaning 청소 1회당 인정하는 봉사 시간 (분)
 * @param rainCompleteDeadlineTime 비 예보 요청 완료 마감 시각 (20:00)
 * @param rainPopThreshold 비 예보 기준: 강수확률 최댓값 (%, 이상)
 * @param rainMmThreshold 비 예보 기준: 강수량 합계 (mm, 이상)
 * @param sameDayMinLeadHours 당일 예보 즉시 발송 조건: 비 시작까지 최소 남은 시간
 * @param sameDayReplyHours 당일 예보 응답 마감: 발송 + 이 시간
 * @param sameDayCompleteBeforeRainHours 당일 예보 완료 마감: 비 시작 이 시간 전
 * @param latestCompleteTime 당일 예보 완료 마감 상한 (밤 이 시각 ~ 06:00에 걸리면 직전 이 시각으로 당김)
 * @param nightDeferredPushTime 야간 푸시 미동의자에게 밤사이 쌓인 알림을 푸시하는 시각
 * @param lonelyIgnoreCount "외로운 또랑이" 표시 기준 연속 무응답 횟수 (표시만, 불이익 없음)
 * @param minRemainingMinutesAfterAdvance 비가 앞당겨져 당긴 완료 마감까지 이보다 적게 남으면 요청 취소 (분)
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
        int restCooldownDays,
        LocalTime nightPushStartTime,
        LocalTime nightPushEndTime,
        int sanctionFirstDays,
        int sanctionSecondDays,
        int sanctionPermanentCount,
        int pauseDays,
        int volunteerMinutesPerCleaning,
        LocalTime rainCompleteDeadlineTime,
        int rainPopThreshold,
        BigDecimal rainMmThreshold,
        int sameDayMinLeadHours,
        int sameDayReplyHours,
        int sameDayCompleteBeforeRainHours,
        LocalTime latestCompleteTime,
        LocalTime nightDeferredPushTime,
        int lonelyIgnoreCount,
        int minRemainingMinutesAfterAdvance) {
}
