package com.rainbutler.domain.adoption.service;

/**
 * 입양 관리 배치가 한 입양에 대해 한 일입니다.
 */
public enum AdoptionCareResult {
    /** 할 일 없음 (아직 주기가 안 됨 · 이미 경고함 · 쉬는 중) */
    NONE,
    /** 미관리 경고를 보냄 */
    WARNED,
    /** 미관리 자동 파양 */
    RELEASED
}
