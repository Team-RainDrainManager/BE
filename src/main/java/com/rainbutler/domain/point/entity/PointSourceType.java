package com.rainbutler.domain.point.entity;

/**
 * 포인트 발생 출처입니다.
 *
 * <p>⚠️ DB에 enum 이름이 문자열로 저장됩니다. 이름을 바꾸면 기존 데이터가 깨지므로 바꾸지 마세요.
 */
public enum PointSourceType {
    /** 청소 */
    CLEANING,
    /** 관리(점검) */
    MANAGEMENT,
    /** 제보 */
    REPORT
}
