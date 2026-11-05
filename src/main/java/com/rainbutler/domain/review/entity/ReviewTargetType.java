package com.rainbutler.domain.review.entity;

import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import java.util.Locale;

/**
 * 관리자 사진 검토 대상 종류입니다.
 *
 * <ul>
 *   <li>{@code INSPECTION}: 점검 · 청소 기록 (inspection)</li>
 *   <li>{@code MANAGEMENT_LOG}: 입양 후 관리 기록 (management_log)</li>
 *   <li>{@code CLEANING}: 입양 전 · 대신 점검 청소 (cleaning)</li>
 * </ul>
 *
 * <p>API 경로에서는 {@code INSPECTION} · {@code MANAGEMENT_LOG} · {@code CLEANING}을 받고,
 * {@code management-log}처럼 하이픈으로 보내도 같은 값으로 봅니다.
 */
public enum ReviewTargetType {
    INSPECTION,
    MANAGEMENT_LOG,
    CLEANING;

    /**
     * 경로 변수를 검토 대상 종류로 바꿉니다.
     *
     * @param value 경로 변수 (대소문자 · 하이픈 무관)
     * @return 검토 대상 종류
     * @throws BusinessException 값이 비었거나 지원하지 않는 종류인 경우 (INVALID_INPUT)
     */
    public static ReviewTargetType parse(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "type은 INSPECTION · MANAGEMENT_LOG · CLEANING 중 하나여야 해요.");
        }
        try {
            return ReviewTargetType.valueOf(
                    value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "type은 INSPECTION · MANAGEMENT_LOG · CLEANING 중 하나여야 해요.");
        }
    }
}
