package com.rainbutler.domain.toolbox.entity;

/**
 * 도구함 상태입니다.
 *
 * <p>⚠️ DB에 enum 이름이 문자열로 저장됩니다. 이름을 바꾸면 기존 데이터가 깨지므로 바꾸지 마세요.
 */
public enum ToolBoxStatus {
    /** 대여 가능 */
    AVAILABLE,
    /** 모두 대여됨 (available_count = 0) */
    ALL_RENTED
}
